package fyi.fyw.etop2p.client.ui

import com.cleanroommc.modularui.api.widget.Interactable
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import com.cleanroommc.modularui.widget.Widget
import fyi.fyw.etolib.ui.Look
import fyi.fyw.etolib.ui.TextWrap
import fyi.fyw.etolib.ui.UiText.tr
import fyi.fyw.etolib.ui.fitTo
import fyi.fyw.etolib.ui.guiWidth
import fyi.fyw.etop2p.client.Actions
import fyi.fyw.etop2p.client.GameTexts
import fyi.fyw.etop2p.client.Listing
import fyi.fyw.etop2p.client.ScreenState
import fyi.fyw.etop2p.data.P2PEntry

private const val PAD = 6
private const val LINE = 11

/**
 * Everything about the tunnel last clicked: what it is, where, what it faces, its connection's note and the other
 * tunnels of the connection, each of which a click picks. With several picked it says how many of what.
 */
internal class DetailPanel(private val state: ScreenState, private val reveal: (P2PEntry) -> Unit) :
    Widget<DetailPanel>(), Interactable {

    /** The connection's other tunnels as drawn this frame: the line's top and the tunnel. */
    private val memberLines = ArrayList<Pair<Int, P2PEntry>>()

    override fun draw(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
        val w = area.w()
        val h = area.h()
        Look.well(0, 0, w, h, Look.SURFACE, Look.KEY_EDGE, Look.RING)
        memberLines.clear()
        val picked = state.selection
        val entry = state.entry(state.primary) ?: picked.lastOrNull()
        if (entry == null) {
            var y = PAD
            for (line in TextWrap.wrap(tr("etop2p.detail.empty"), w - 2 * PAD)) {
                Look.text(line, PAD, y, Look.MUTED)
                y += LINE
            }
            return
        }
        val meta = state.meta(entry.frequency)
        var y = PAD
        Look.item(GameTexts.typeStack(entry.type), PAD, y, 16, context.currentDrawingZ)
        val title = Listing.title(entry, GameTexts)
        val type = GameTexts.typeName(entry.type).takeIf { it != title }
        Look.text(title.fitTo(w - 2 * PAD - 20), PAD + 20, if (type == null) y + 4 else y, Look.INK)
        if (type != null) Look.text(type.fitTo(w - 2 * PAD - 20), PAD + 20, y + 10, Look.MUTED)
        y += 24

        fun row(label: String, value: String, ink: Int = Look.INK) {
            Look.text(label, PAD, y, Look.MUTED)
            val room = w - 2 * PAD - label.guiWidth - 6
            Look.textRight(value.fitTo(room), w - PAD, y, ink)
            y += LINE
        }
        when {
            entry.frequency == 0L -> row(tr("etop2p.tip.role"), tr("etop2p.role.unbound"), Look.OFF_INK)
            entry.output -> row(tr("etop2p.tip.role"), tr("etop2p.role.out"), Look.PRODUCT_INK)
            else -> row(tr("etop2p.tip.role"), tr("etop2p.role.in"), Look.OK_INK)
        }
        if (entry.name.isNotEmpty() && entry.name != title) row(tr("etop2p.tip.connection"), entry.name)
        if (entry.label.isNotEmpty() && entry.label != title) row(tr("etop2p.detail.label"), entry.label)
        row(tr("etop2p.tip.place"), "${entry.key.x}, ${entry.key.y}, ${entry.key.z}")
        row(tr("etop2p.detail.side"), Actions.sideName(entry.key.side))
        val distance = GameTexts.distance(entry)
        row(
            tr("etop2p.tip.distance"),
            if (distance == Double.MAX_VALUE) tr("etop2p.dim", entry.key.dim) else tr("etop2p.metres", distance.toInt()),
        )
        if (entry.frequency != 0L) {
            row(tr("etop2p.tip.frequency"), Listing.frequencyCode(entry.frequency), Palette.of(entry.frequency, meta))
        }
        if (entry.channels >= 0) row(tr("etop2p.tip.channels"), entry.channels.toString())
        if (!entry.online) row(tr("etop2p.tip.state"), tr("etop2p.offline"), Look.RED_INK)
        GameTexts.attachedStack(entry)?.let { stack ->
            Look.text(tr("etop2p.detail.attached"), PAD, y + 2, Look.MUTED)
            Look.item(stack, w - PAD - 12, y, 12, context.currentDrawingZ)
            val room = w - 2 * PAD - tr("etop2p.detail.attached").guiWidth - 20
            Look.textRight(GameTexts.attachedName(entry).fitTo(room), w - PAD - 15, y + 2, Look.INK)
            y += 14
        }
        if (meta.note.isNotEmpty()) {
            y += 3
            for (line in TextWrap.wrap(meta.note, w - 2 * PAD).take(3)) {
                Look.text(line, PAD, y, Look.GOLD)
                y += LINE
            }
        }
        val others = state.connection(entry).filter { it.key != entry.key }.sortedBy { it.output }
        if (others.isEmpty()) return
        y += 3
        Look.rect(PAD, y, w - 2 * PAD, 1, Look.RULE)
        y += 4
        Look.text(tr("etop2p.detail.members", others.size), PAD, y, Look.MUTED)
        y += LINE
        val my = context.absMouseY - area.y()
        for (other in others) {
            if (y + LINE > h - 2) break
            val hot = isHovering && my >= y - 1 && my < y + LINE - 1
            if (hot) Look.rect(2, y - 1, w - 4, LINE, Look.MENU_HOVER)
            val tag = tr(if (other.output) "etop2p.role.out" else "etop2p.role.in")
            Look.text(tag, PAD, y, if (other.output) Look.PRODUCT_INK else Look.OK_INK)
            val x = PAD + tag.guiWidth + 4
            Look.text(Listing.title(other, GameTexts).fitTo(w - PAD - x), x, y, if (other.online) Look.INK else Look.MUTED)
            memberLines += y to other
            y += LINE
        }
    }

    override fun onMousePressed(mouseButton: Int): Interactable.Result {
        if (mouseButton != 0) return Interactable.Result.IGNORE
        val my = context.absMouseY - area.y()
        val other = memberLines.firstOrNull { (top, _) -> my >= top - 1 && my < top + LINE - 1 }?.second
            ?: return Interactable.Result.IGNORE
        Look.click()
        state.select(listOf(other.key))
        reveal(other)
        return Interactable.Result.SUCCESS
    }
}
