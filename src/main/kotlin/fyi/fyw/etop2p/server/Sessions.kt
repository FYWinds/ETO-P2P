package fyi.fyw.etop2p.server

import appeng.api.networking.IGrid
import cpw.mods.fml.common.FMLCommonHandler
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.PlayerEvent
import cpw.mods.fml.common.gameevent.TickEvent
import fyi.fyw.etop2p.data.LinkMeta
import fyi.fyw.etop2p.data.Notice
import fyi.fyw.etop2p.data.Op
import fyi.fyw.etop2p.data.P2PEntry
import fyi.fyw.etop2p.data.P2PKey
import fyi.fyw.etop2p.net.Net
import java.util.UUID
import net.minecraft.entity.player.EntityPlayerMP

/**
 * The screens that are open: for each player the network it shows, what the client has been told about it, and the
 * steps that can be undone. A screen is told what changed after each of its operations and once a second besides,
 * so changes made in the world and by other players show up too.
 */
object Sessions {

    /** One operation as done: what its tunnels, labels and tags were before and after. */
    private class Step(
        val before: List<TunnelState>,
        val after: List<TunnelState>,
        val labelsBefore: Map<P2PKey, String> = emptyMap(),
        val labelsAfter: Map<P2PKey, String> = emptyMap(),
        val linksBefore: Map<Long, LinkMeta> = emptyMap(),
        val linksAfter: Map<Long, LinkMeta> = emptyMap(),
    )

    private class Session(val player: EntityPlayerMP, var grid: IGrid) {
        val sent = HashMap<P2PKey, P2PEntry>()
        var sentLinks: Map<Long, LinkMeta> = emptyMap()
        val undo = ArrayDeque<Step>()
    }

    private const val UNDO_STEPS = 20
    private const val PUSH_EVERY_TICKS = 20
    private const val MAX_TEXT = 256

    private val sessions = HashMap<UUID, Session>()
    private var ticks = 0

    fun register() {
        FMLCommonHandler.instance().bus().register(this)
    }

    fun clear() = sessions.clear()

    /** The network of the screen [player] has open. */
    fun gridOf(player: EntityPlayerMP): IGrid? = sessions[player.uniqueID]?.grid

    /** Opens the screen of [player] on [grid]. The caller has checked the build permission. */
    fun open(player: EntityPlayerMP, grid: IGrid, focus: P2PKey?) {
        val session = Session(player, grid)
        sessions[player.uniqueID] = session
        val store = LinkStore.get()
        Tunnels.scan(grid).forEach { (key, tunnel) -> session.sent[key] = Tunnels.entry(key, tunnel, store.label(key)) }
        session.sentLinks = links(session.sent.values, store)
        Net.channel.sendTo(Net.Open(session.sent.values.toList(), session.sentLinks, focus?.takeIf { it in session.sent }), player)
    }

    fun handle(player: EntityPlayerMP, op: Op) {
        val session = sessions[player.uniqueID] ?: return
        if (op == Op.Close) {
            sessions.remove(player.uniqueID)
            return
        }
        if (!alive(session)) return
        if (!Tunnels.mayBuild(session.grid, player)) {
            push(session, Notice("etop2p.notice.denied", emptyList(), true))
            return
        }
        push(session, perform(session, op))
    }

    private fun perform(session: Session, op: Op): Notice {
        val ops = P2POps(session.player, session.grid)
        val store = LinkStore.get()
        return when (op) {
            is Op.Label -> {
                val labels = op.labels.filter { it.first in session.sent }.associate { it.first to it.second.take(MAX_TEXT) }
                if (labels.isEmpty()) return nothing()
                remember(session, Step(emptyList(), emptyList(), labels.keys.associateWith(store::label), labels))
                labels.forEach(store::setLabel)
                Notice("etop2p.notice.labelled", listOf(labels.size.toString()), false)
            }
            is Op.Meta -> {
                val frequencies = op.frequencies.filter { it != 0L }.distinct()
                if (frequencies.isEmpty()) return nothing()
                val before = frequencies.associateWith(store::link)
                val after = before.mapValues { (_, meta) ->
                    meta.copy(
                        color = op.color ?: meta.color,
                        favorite = op.favorite ?: meta.favorite,
                        note = op.note?.take(MAX_TEXT) ?: meta.note,
                    )
                }
                remember(session, Step(emptyList(), emptyList(), linksBefore = before, linksAfter = after))
                after.forEach(store::setLink)
                Notice("etop2p.notice.tagged", listOf(after.size.toString()), false)
            }
            Op.Undo -> undo(session, ops, store)
            else -> {
                val targets = ops.plan(op, Tunnels.scan(session.grid))
                if (targets.isEmpty()) return nothing()
                val before = ops.states(targets.map { it.key })
                val after = ops.apply(targets)
                if (after != before) remember(session, Step(before, after))
                val wanted = targets.associateBy { it.key }
                val failed = targets.size - after.count { it == wanted[it.key] }
                if (failed > 0) Notice("etop2p.notice.partly", listOf((targets.size - failed).toString(), failed.toString()), true)
                else Notice("etop2p.notice.done", listOf(targets.size.toString()), false)
            }
        }
    }

    private fun nothing() = Notice("etop2p.notice.nothing", emptyList(), true)

    private fun remember(session: Session, step: Step) {
        session.undo.addLast(step)
        if (session.undo.size > UNDO_STEPS) session.undo.removeFirst()
    }

    /** Takes the last step back, unless its tunnels are no longer as that step left them. */
    private fun undo(session: Session, ops: P2POps, store: LinkStore): Notice {
        val step = session.undo.lastOrNull() ?: return nothing()
        val unchanged = ops.states(step.after.map { it.key }) == step.after &&
            step.labelsAfter.all { (key, label) -> store.label(key) == label } &&
            step.linksAfter.all { (frequency, meta) -> store.link(frequency) == meta }
        session.undo.removeLast()
        if (!unchanged) return Notice("etop2p.notice.undo_stale", emptyList(), true)
        ops.apply(step.before)
        step.labelsBefore.forEach(store::setLabel)
        step.linksBefore.forEach(store::setLink)
        return Notice("etop2p.notice.undone", emptyList(), false)
    }

    /** Whether the session's network is still there, looking it up again through its tunnels if AE2 rebuilt it. */
    private fun alive(session: Session): Boolean {
        if (session.grid.pivot != null && !session.grid.isEmpty) return true
        val grid = session.sent.keys.asSequence().mapNotNull { Tunnels.at(it) }.mapNotNull { Tunnels.gridOf(it) }.firstOrNull()
        if (grid != null) {
            session.grid = grid
            return true
        }
        sessions.remove(session.player.uniqueID)
        Net.channel.sendTo(Net.Update(closed = true, notice = Notice("etop2p.notice.gone", emptyList(), true)), session.player)
        return false
    }

    private fun links(entries: Collection<P2PEntry>, store: LinkStore): Map<Long, LinkMeta> {
        val links = HashMap<Long, LinkMeta>()
        for (entry in entries) {
            if (entry.frequency == 0L || entry.frequency in links) continue
            val meta = store.link(entry.frequency)
            if (!meta.isEmpty) links[entry.frequency] = meta
        }
        return links
    }

    /** Tells the client what is different from what it was last told, with [notice] if this answers an operation. */
    private fun push(session: Session, notice: Notice?) {
        val store = LinkStore.get()
        val now = HashMap<P2PKey, P2PEntry>()
        Tunnels.scan(session.grid).forEach { (key, tunnel) -> now[key] = Tunnels.entry(key, tunnel, store.label(key)) }
        val changed = now.values.filter { session.sent[it.key] != it }
        val removed = session.sent.keys.filter { it !in now }
        val links = links(now.values, store)
        val linksChanged = links != session.sentLinks
        if (changed.isEmpty() && removed.isEmpty() && !linksChanged && notice == null) return
        session.sent.clear()
        session.sent.putAll(now)
        session.sentLinks = links
        Net.channel.sendTo(
            Net.Update(changed, removed, if (linksChanged) links else null, session.undo.size, notice),
            session.player,
        )
    }

    @SubscribeEvent
    fun onServerTick(event: TickEvent.ServerTickEvent) {
        if (event.phase != TickEvent.Phase.END || sessions.isEmpty() || ++ticks % PUSH_EVERY_TICKS != 0) return
        for (session in sessions.values.toList()) if (alive(session)) push(session, null)
    }

    @SubscribeEvent
    fun onLogout(event: PlayerEvent.PlayerLoggedOutEvent) {
        sessions.remove(event.player.uniqueID)
    }
}
