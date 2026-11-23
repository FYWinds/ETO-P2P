package fyi.fyw.etop2p.server

import appeng.api.networking.IGrid
import appeng.api.parts.PartItemStack
import appeng.parts.p2p.PartP2PTunnel
import fyi.fyw.etop2p.data.Op
import fyi.fyw.etop2p.data.P2PKey
import fyi.fyw.etop2p.data.P2PTypes
import java.util.concurrent.ThreadLocalRandom
import net.minecraft.entity.player.EntityPlayer

/** What an operation changes about one tunnel. */
internal data class TunnelState(
    val key: P2PKey,
    val type: Int,
    val frequency: Long,
    val output: Boolean,
    val name: String,
)

/**
 * Changes the tunnels of one network for one player. Every operation is first worked out as the states its tunnels
 * should end in ([plan]) and then carried out by one routine ([apply]) through AE2's own conversions, so undoing an
 * operation is applying the states from before it.
 */
internal class P2POps(private val player: EntityPlayer, private val grid: IGrid) {

    fun state(key: P2PKey, tunnel: PartP2PTunnel<*>) =
        TunnelState(key, Tunnels.typeOf(tunnel), tunnel.frequency, tunnel.isOutput, Tunnels.nameOf(tunnel))

    /** The tunnel at [key] if it is loaded and still part of this network. */
    private fun mine(key: P2PKey): PartP2PTunnel<*>? = Tunnels.at(key)?.takeIf { Tunnels.gridOf(it) === grid }

    fun states(keys: Collection<P2PKey>): List<TunnelState> = keys.mapNotNull { key -> mine(key)?.let { state(key, it) } }

    /** The states [op] asks for, given the network's tunnels as they are; empty when it asks for nothing possible. */
    fun plan(op: Op, tunnels: Map<P2PKey, PartP2PTunnel<*>>): List<TunnelState> {
        fun now(key: P2PKey) = tunnels[key]?.let { state(key, it) }
        fun connection(of: TunnelState): List<TunnelState> =
            if (of.frequency == 0L) listOf(of)
            else tunnels.filter { it.value.frequency == of.frequency }.map { state(it.key, it.value) }
        return when (op) {
            is Op.Bind -> {
                val input = now(op.input) ?: return emptyList()
                val bound = !input.output && input.frequency != 0L
                val frequency = if (bound) input.frequency else fresh(tunnels.values)
                listOf(input.copy(frequency = frequency, output = false)) + op.outputs.distinct()
                    .filter { it != op.input }
                    .mapNotNull(::now)
                    .map { it.copy(type = input.type, frequency = frequency, output = true, name = input.name) }
            }
            is Op.MakeInput -> {
                val chosen = now(op.key)?.takeIf { it.output && it.frequency != 0L } ?: return emptyList()
                connection(chosen).map { it.copy(output = it.key != chosen.key) }
            }
            // The name was the connection's; a tunnel that left it does not go on carrying it.
            is Op.Unbind -> op.keys.distinct().mapNotNull(::now).map { it.copy(frequency = 0L, output = false, name = "") }
            is Op.Convert -> {
                if (P2PTypes.stack(op.type) == null) return emptyList()
                op.keys.mapNotNull(::now).flatMap(::connection).distinctBy { it.key }.map { it.copy(type = op.type) }
            }
            is Op.Rename -> {
                val named = LinkedHashMap<P2PKey, TunnelState>()
                for ((key, name) in op.names) {
                    val tunnel = now(key) ?: continue
                    connection(tunnel).forEach { named[it.key] = it.copy(name = name.take(MAX_NAME)) }
                }
                named.values.toList()
            }
            else -> emptyList()
        }
    }

    /** Brings the tunnels to [targets], as far as each tunnel allows its new type; returns the states they end in. */
    fun apply(targets: List<TunnelState>): List<TunnelState> {
        // An output takes its name and settings from its input, so the inputs come first.
        val ordered = targets.sortedBy { if (it.frequency == 0L) 0 else if (!it.output) 1 else 2 }
        for (target in ordered) mine(target.key)?.let { reshape(it, target) }
        for (target in ordered) {
            val tunnel = mine(target.key) ?: continue
            if (Tunnels.nameOf(tunnel) != target.name) Tunnels.rename(tunnel, target.name)
        }
        return states(targets.map { it.key })
    }

    private fun reshape(start: PartP2PTunnel<*>, target: TunnelState) {
        var tunnel = start
        val sameType = Tunnels.typeOf(tunnel) == target.type
        val stack = (if (sameType) tunnel.getItemStack(PartItemStack.Wrench)?.copy() else P2PTypes.stack(target.type)) ?: return
        if (target.frequency == 0L) {
            if (tunnel.frequency != 0L) tunnel = tunnel.unbind(player)
            if (!sameType) tunnel.convertToInput(player, stack)?.unbind(player)
        } else if (!target.output) {
            // AE2 leaves a bound input as it is, also when asked for another type.
            if (!sameType && !tunnel.isOutput && tunnel.frequency != 0L) tunnel = tunnel.unbind(player)
            if (tunnel.isOutput || tunnel.frequency == 0L) tunnel = tunnel.convertToInput(player, stack) ?: return
            // AE2 gave it the time as its frequency.
            if (tunnel.frequency != target.frequency) {
                val table = tunnel.proxy.p2P
                // The network keeps one input per frequency and forgets it when any input of that frequency is
                // taken out, so the one that has it now gives it up first (it is on its way to being an output).
                val holder = table.getInput(target.frequency)
                if (holder != null && holder !== tunnel) holder.unbind(player)
                table.updateFreq(tunnel, target.frequency)
            }
        } else if (!(tunnel.isOutput && tunnel.frequency == target.frequency && sameType)) {
            tunnel.convertToOutput(player, stack, target.frequency)
        }
    }

    companion object {
        const val MAX_NAME = 64

        /**
         * A frequency no tunnel of the network has. Not the time, as AE2's are: a tunnel AE2 turns into an input
         * within the same millisecond would take over that frequency's place in the network's table for a moment.
         */
        fun fresh(tunnels: Collection<PartP2PTunnel<*>>): Long {
            val used = tunnels.mapTo(HashSet()) { it.frequency }
            while (true) {
                val frequency = ThreadLocalRandom.current().nextLong() and Long.MAX_VALUE
                if (frequency != 0L && frequency !in used) return frequency
            }
        }
    }
}
