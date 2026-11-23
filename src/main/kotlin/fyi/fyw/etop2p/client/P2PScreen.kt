package fyi.fyw.etop2p.client

import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.screen.ModularScreen
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import fyi.fyw.etolib.ui.Chip
import fyi.fyw.etolib.ui.IconButton
import fyi.fyw.etolib.ui.Icons
import fyi.fyw.etolib.ui.Look
import fyi.fyw.etolib.ui.Menu
import fyi.fyw.etolib.ui.SearchField
import fyi.fyw.etolib.ui.UiText.tr
import fyi.fyw.etop2p.EtoP2P
import fyi.fyw.etop2p.client.ui.DetailPanel
import fyi.fyw.etop2p.client.ui.P2PList
import fyi.fyw.etop2p.client.ui.StatusLine
import fyi.fyw.etop2p.data.Op
import fyi.fyw.etop2p.data.P2PEntry
import fyi.fyw.etop2p.data.P2PTypes

private const val EDGE = 6
private const val BAR_H = 16
private const val DETAIL_W = 138
private const val LIST_TOP = EDGE + 2 * (BAR_H + 4)
private const val STATUS_H = 12

/**
 * The card's screen: a search box and the narrowing chips over the list of tunnels, the details of the picked one
 * beside it, a status line under it.
 */
class P2PScreen private constructor(panel: ModularPanel) : ModularScreen(EtoP2P.MOD_ID, panel) {

    override fun onClose() {
        super.onClose()
        if (ClientState.screen !== this) return
        ClientState.screen = null
        if (ClientState.live) ClientState.send(Op.Close)
    }

    /** The screen's one panel, in the kit's look instead of the theme's. */
    private class Board : ModularPanel("etop2p_main") {
        override fun drawBackground(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
            val w = area.w()
            val h = area.h()
            Look.dropShadow(0, 0, w, h)
            Look.bevel(0, 0, w, h, Look.FRAME, Look.HIGHLIGHT, Look.SHADOW, Look.KEY_EDGE)
        }
    }

    companion object {
        fun create(): P2PScreen {
            val state = ScreenState().also { ScreenState.current = it }
            val panel = Board()
            panel.widthRel(0.94f).heightRel(0.92f)
            state.panel = panel
            val actions = Actions(state)
            val list = P2PList(state, actions)

            // Top row: the search box, then how to order and the step back.
            lateinit var sortKey: IconButton
            sortKey = IconButton(tr("etop2p.sort")) {
                val at = sortKey.area
                Menu.open(panel, at.x(), at.y() + at.h() + 1) {
                    Sort.entries.forEach { sort ->
                        entry(tr("etop2p.sort." + sort.name.lowercase()), checked = state.sort == sort) { state.sort = sort }
                    }
                }
            }
            val sortW = sortKey.defaultWidth
            val undo = IconButton(Icons.UNDO, tip = tr("etop2p.undo.tip"), enabled = { ClientState.undo > 0 }) { actions.undo() }
            panel.child(undo.right(EDGE).top(EDGE).size(BAR_H, BAR_H))
            panel.child(sortKey.right(EDGE + BAR_H + 4).top(EDGE).size(sortW, BAR_H))
            panel.child(
                SearchField(tr("etop2p.search")) { state.filter = state.filter.copy(text = it) }
                    .left(EDGE).right(EDGE + BAR_H + 4 + sortW + 4).top(EDGE).height(BAR_H),
            )

            // Second row: what to show at the left, how to show it at the right.
            var x = EDGE
            fun chip(chip: Chip) {
                panel.child(chip.left(x).top(EDGE + BAR_H + 4).size(chip.defaultWidth, BAR_H))
                x += chip.defaultWidth + 3
            }
            lateinit var typeChip: Chip
            typeChip = Chip(tr("etop2p.filter.type"), selected = { state.filter.types.isNotEmpty() }) {
                val at = typeChip.area
                Menu.open(panel, at.x(), at.y() + at.h() + 1) {
                    entry(tr("etop2p.filter.type.all"), checked = state.filter.types.isEmpty()) {
                        state.filter = state.filter.copy(types = emptySet())
                    }
                    separator()
                    P2PTypes.available.forEach { type ->
                        val on = type.index in state.filter.types
                        entry(GameTexts.typeName(type.index), item = type.stack, checked = on) {
                            val types = state.filter.types
                            state.filter = state.filter.copy(types = if (on) types - type.index else types + type.index)
                        }
                    }
                }
            }
            chip(typeChip)
            fun tri(key: String, read: (Filter) -> Tri, want: Tri, tip: String? = null, write: (Filter, Tri) -> Filter) =
                chip(
                    Chip(tr("etop2p.filter.$key"), tip = tip, selected = { read(state.filter) == want }) {
                        state.filter = write(state.filter, if (read(state.filter) == want) Tri.ANY else want)
                    },
                )
            tri("in", { it.output }, Tri.NO) { filter, value -> filter.copy(output = value) }
            tri("out", { it.output }, Tri.YES) { filter, value -> filter.copy(output = value) }
            tri("bound", { it.bound }, Tri.YES) { filter, value -> filter.copy(bound = value) }
            tri("unbound", { it.bound }, Tri.NO) { filter, value -> filter.copy(bound = value) }
            tri("offline", { it.online }, Tri.NO, tr("etop2p.filter.offline.tip")) { filter, value -> filter.copy(online = value) }
            chip(
                Chip(tr("etop2p.filter.favorite"), selected = { state.filter.favorites }) {
                    state.filter = state.filter.copy(favorites = !state.filter.favorites)
                },
            )
            var right = EDGE
            View.entries.reversed().forEach { view ->
                val chip = Chip(tr("etop2p.view." + view.name.lowercase()), selected = { state.view == view }) {
                    state.view = view
                }
                panel.child(chip.right(right).top(EDGE + BAR_H + 4).size(chip.defaultWidth, BAR_H))
                right += chip.defaultWidth + 3
            }

            // The list, the details beside it with their keys under them, the status line.
            val bottom = EDGE + STATUS_H
            panel.child(list.left(EDGE).right(EDGE + DETAIL_W + 4).top(LIST_TOP).bottom(bottom))
            val reveal = { entry: P2PEntry ->
                state.collapsed -= entry.frequency
                list.revealNext = entry
            }
            panel.child(DetailPanel(state, reveal).right(EDGE).width(DETAIL_W).top(LIST_TOP).bottom(bottom + BAR_H + 4))
            val picked = { state.selected.isNotEmpty() }
            val keys = listOf(
                IconButton(tr("etop2p.key.rename"), tip = tr("etop2p.key.rename.tip"), enabled = picked) { actions.rename() },
                IconButton(tr("etop2p.key.label"), tip = tr("etop2p.key.label.tip"), enabled = picked) { actions.label() },
                IconButton(tr("etop2p.key.unbind"), enabled = picked) { actions.unbind() },
                IconButton(tr("etop2p.key.locate"), tip = tr("etop2p.key.locate.tip"), enabled = picked) { actions.locate() },
            )
            val keyW = (DETAIL_W - 3 * 2) / keys.size
            keys.forEachIndexed { i, key ->
                panel.child(key.right(EDGE + (keys.size - 1 - i) * (keyW + 2)).width(keyW).height(BAR_H).bottom(bottom))
            }
            panel.child(StatusLine(state).left(EDGE).right(EDGE).bottom(3).height(STATUS_H - 2))

            // The tunnel the card was used on starts out picked and in sight.
            ClientState.focus?.let { focus ->
                state.select(listOf(focus))
                list.revealNext = state.entry(focus)
                ClientState.focus = null
            }
            return P2PScreen(panel)
        }
    }
}
