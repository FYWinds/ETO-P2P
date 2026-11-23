package fyi.fyw.etop2p.client.ui

import com.cleanroommc.modularui.api.UpOrDown
import com.cleanroommc.modularui.api.widget.IGuiAction
import com.cleanroommc.modularui.api.widget.Interactable
import com.cleanroommc.modularui.drawable.Stencil
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import com.cleanroommc.modularui.widget.Widget
import fyi.fyw.etolib.ui.Icons
import fyi.fyw.etolib.ui.Look
import fyi.fyw.etolib.ui.ScrollBar
import fyi.fyw.etolib.ui.Tip
import fyi.fyw.etolib.ui.UiText.tr
import fyi.fyw.etolib.ui.fitTo
import fyi.fyw.etolib.ui.guiWidth
import fyi.fyw.etop2p.client.Actions
import fyi.fyw.etop2p.client.ClientState
import fyi.fyw.etop2p.client.GameTexts
import fyi.fyw.etop2p.client.Group
import fyi.fyw.etop2p.client.Listing
import fyi.fyw.etop2p.client.ScreenState
import fyi.fyw.etop2p.client.View
import fyi.fyw.etop2p.data.P2PEntry
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.lwjgl.input.Keyboard

private const val ROW_H = 14
private const val CARD_H = 30
private const val CARD_MIN = 124
private const val HEAD_H = 16
private const val GAP = 3
private const val INDENT = 8
private const val WHEEL_STEP = 3 * ROW_H

/** How far the mouse moves with the button down before a press becomes a drag. */
private const val DRAG_AFTER = 4

/**
 * The tunnels, drawn by this one widget in the screen's view: as connections with their tunnels as cards under a
 * header each, as cards alone, or as rows. Only what is in sight is drawn. A click picks (Ctrl adds, Shift picks the
 * run from the last pick), a press on empty room pulls a box that picks what it touches, the right button opens the
 * menu for what is picked, and picked tunnels dragged onto another tunnel or a header are bound there.
 */
internal class P2PList(private val state: ScreenState, private val actions: Actions) : Widget<P2PList>(), Interactable {

    /** A place in the list: a connection's header or a tunnel. Positions are in the list's own, unscrolled pixels. */
    private sealed class Cell(val x: Int, val y: Int, val w: Int, val h: Int) {
        fun holds(px: Int, py: Int) = px >= x && px < x + w && py >= y && py < y + h

        fun touches(x0: Int, y0: Int, x1: Int, y1: Int) = x < x1 && x + w > x0 && y < y1 && y + h > y0
    }

    private class Head(val group: Group, x: Int, y: Int, w: Int, h: Int) : Cell(x, y, w, h)

    private class Card(val entry: P2PEntry, val row: Boolean, x: Int, y: Int, w: Int, h: Int) : Cell(x, y, w, h)

    private enum class Gesture { NONE, PRESSED, DRAG, BOX, BAR }

    private var cells: List<Cell> = emptyList()
    private var contentHeight = 0
    private var laidFor: Any? = null
    private var scroll = 0
    private val bar = ScrollBar()

    private var gesture = Gesture.NONE
    private var pressX = 0
    private var pressY = 0

    /** The picked tunnel a press landed on: picked alone on release, unless the press became a drag. */
    private var pressedPicked: P2PEntry? = null

    init {
        // The screen hears the button come up wherever the mouse is by then: a drag past the list's edge still ends here.
        listenGuiAction(IGuiAction.MouseReleased { button ->
            if (button == 0) finish()
            false
        })
    }

    private val mouseX: Int get() = context.absMouseX - area.x()
    private val mouseY: Int get() = context.absMouseY - area.y()

    /** The mouse in the list's unscrolled pixels. */
    private val listY: Int get() = mouseY + scroll

    private fun cellAt(px: Int, py: Int): Cell? = cells.firstOrNull { it.holds(px, py) }

    private val cards: List<Card> get() = cells.filterIsInstance<Card>()

    private fun layout() {
        val width = area.w() - ScrollBar.W - 3
        val wanted = listOf(state.built, state.view, width, state.collapsed.toSet())
        if (wanted == laidFor) return
        laidFor = wanted
        val out = ArrayList<Cell>()
        var y = 0
        fun grid(entries: List<P2PEntry>, left: Int) {
            val room = width - left
            val columns = max(1, (room + GAP) / (CARD_MIN + GAP))
            val cardW = (room - GAP * (columns - 1)) / columns
            entries.forEachIndexed { i, entry ->
                out += Card(entry, false, left + i % columns * (cardW + GAP), y + i / columns * (CARD_H + GAP), cardW, CARD_H)
            }
            y += (entries.size + columns - 1) / columns * (CARD_H + GAP)
        }
        when (state.view) {
            View.LIST -> state.shown.forEach {
                out += Card(it, true, 0, y, width, ROW_H)
                y += ROW_H
            }
            View.CARDS -> grid(state.shown, 0)
            View.GROUPED -> state.groups.forEach { group ->
                out += Head(group, 0, y, width, HEAD_H)
                y += HEAD_H + GAP
                if (group.frequency !in state.collapsed) grid(group.members, INDENT)
                y += GAP
            }
        }
        cells = out
        contentHeight = y
    }

    override fun draw(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
        state.refresh()
        layout()
        revealNext?.let(::reveal)
        revealNext = null
        follow()
        val w = area.w()
        val h = area.h()
        Look.well(0, 0, w, h, Look.SURFACE, Look.KEY_EDGE, Look.RING)
        val hovered = if (isHovering && gesture != Gesture.BOX && gesture != Gesture.BAR) cellAt(mouseX, listY) else null
        val dropTarget = if (gesture == Gesture.DRAG) dropTarget(hovered) else null
        val z = context.currentDrawingZ
        Stencil.apply(1, 1, w - 2, h - 2, context)
        for (cell in cells) {
            val top = cell.y - scroll + 2
            if (top + cell.h < 0 || top > h) continue
            val left = cell.x + 2
            when (cell) {
                is Head -> drawHead(cell.group, left, top, cell.w, cell === hovered, cell === dropTarget)
                is Card -> {
                    val lit = cell === hovered || cell === dropTarget
                    if (cell.row) drawRow(cell.entry, left, top, cell.w, lit, cell === dropTarget, z)
                    else drawCard(cell.entry, left, top, cell.w, lit, cell === dropTarget, z)
                }
            }
        }
        if (cells.isEmpty()) {
            val words = if (ClientState.entries.isEmpty()) "etop2p.list.none" else "etop2p.list.no_match"
            Look.textCentered(tr(words), w / 2, h / 2 - 4, Look.MUTED)
        }
        if (gesture == Gesture.BOX) {
            val x0 = min(pressX, mouseX)
            val y0 = min(pressY, listY) - scroll
            Look.rect(x0, y0, abs(mouseX - pressX), abs(listY - pressY), 0x3022D3EE)
            Look.ring(x0, y0, abs(mouseX - pressX), abs(listY - pressY), 1, Look.SELECTION)
        }
        Stencil.remove()
        scroll = bar.draw(w - 1, 1, h - 2, contentHeight + 4, scroll, isHovering && bar.contains(mouseX, mouseY))
        if (gesture == Gesture.DRAG) {
            val text = tr("etop2p.list.dragging", state.selected.size)
            Look.onTop {
                Look.rect(mouseX + 8, mouseY + 6, text.guiWidth + 8, 12, 0xE0111317.toInt())
                Look.text(text, mouseX + 12, mouseY + 8, Look.GOLD)
            }
        } else if (gesture == Gesture.NONE && hovered is Card) {
            Tip.hover(hovered.entry.key) { tipFor(hovered.entry) }
        }
    }

    /** A press that goes on, followed each frame until the button comes up. */
    private fun follow() {
        when (gesture) {
            Gesture.BAR -> scroll = bar.drag(mouseY)
            Gesture.PRESSED ->
                if (abs(mouseX - pressX) > DRAG_AFTER || abs(listY - pressY) > DRAG_AFTER) {
                    gesture = if (state.selected.isEmpty()) Gesture.BOX else Gesture.DRAG
                }
            Gesture.BOX -> {
                val x0 = min(pressX, mouseX)
                val x1 = max(pressX, mouseX)
                val y0 = min(pressY, listY)
                val y1 = max(pressY, listY)
                state.select(cards.filter { it.touches(x0, y0, x1, y1) }.map { it.entry.key })
                edgeScroll()
            }
            Gesture.DRAG -> edgeScroll()
            Gesture.NONE -> {}
        }
    }

    /** Near the top or bottom edge with the button held, the list moves under the mouse. */
    private fun edgeScroll() {
        if (mouseY < 8) scroll -= 4 else if (mouseY > area.h() - 8) scroll += 4
        scroll = scroll.coerceIn(0, max(0, contentHeight + 4 - (area.h() - 2)))
    }

    /** Where the dragged tunnels would land: a tunnel or a header that is not among them. */
    private fun dropTarget(under: Cell?): Cell? = when (under) {
        is Card -> under.takeIf { it.entry.key !in state.selected }
        is Head -> under.takeIf { group -> group.group.members.any { it.key !in state.selected } || !group.group.bound }
        null -> null
    }

    private fun finish() {
        val was = gesture
        gesture = Gesture.NONE
        when (was) {
            Gesture.BAR -> bar.release()
            Gesture.PRESSED -> pressedPicked?.let { state.select(listOf(it.key)) }
            Gesture.DRAG -> when (val target = dropTarget(if (isHovering) cellAt(mouseX, listY) else null)) {
                is Card -> actions.dropOn(target.entry)
                // Onto a connection: under its input. Onto the unbound tunnels: unbound.
                is Head -> target.group.input?.let(actions::bindTo) ?: if (!target.group.bound) actions.unbind() else Unit
                null -> {}
            }
            else -> {}
        }
        pressedPicked = null
    }

    override fun onMousePressed(mouseButton: Int): Interactable.Result {
        if (mouseButton == 0 && bar.contains(mouseX, mouseY)) {
            scroll = bar.press(mouseY, scroll)
            gesture = Gesture.BAR
            return Interactable.Result.SUCCESS
        }
        val cell = cellAt(mouseX, listY)
        val add = Interactable.hasControlDown()
        when (mouseButton) {
            0 -> {
                pressX = mouseX
                pressY = listY
                pressedPicked = null
                when (cell) {
                    is Card -> pressCard(cell.entry, add)
                    is Head ->
                        if (mouseX < 14) {
                            cell.group.frequency.let { if (!state.collapsed.remove(it)) state.collapsed += it }
                        } else {
                            state.select(cell.group.members.map { it.key }, add)
                            gesture = Gesture.PRESSED
                        }
                    null -> {
                        if (!add) state.select(emptyList())
                        gesture = Gesture.PRESSED
                    }
                }
            }
            1 -> {
                val keys = when (cell) {
                    is Card -> listOf(cell.entry.key)
                    is Head -> cell.group.members.map { it.key }
                    null -> return Interactable.Result.SUCCESS
                }
                // A menu is about what is picked: a click outside the picked ones picks anew.
                if (!state.selected.containsAll(keys)) state.select(keys)
                state.primary = keys.first().takeIf { cell is Card } ?: keys.last()
                actions.openMenu(context.absMouseX, context.absMouseY)
            }
            else -> return Interactable.Result.IGNORE
        }
        return Interactable.Result.SUCCESS
    }

    private fun pressCard(entry: P2PEntry, add: Boolean) {
        val anchor = state.anchor
        when {
            Interactable.hasShiftDown() && anchor != null -> {
                val order = cards.map { it.entry.key }
                val from = order.indexOf(anchor)
                val to = order.indexOf(entry.key)
                if (from >= 0 && to >= 0) {
                    state.selected += order.subList(min(from, to), max(from, to) + 1)
                    state.primary = entry.key
                }
            }
            add ->
                if (!state.selected.remove(entry.key)) state.select(listOf(entry.key), add = true)
                else state.primary = state.selected.lastOrNull()
            entry.key in state.selected -> {
                // Maybe the start of dragging them all; a plain click picks this one alone when the button comes up.
                pressedPicked = entry
                state.primary = entry.key
            }
            else -> state.select(listOf(entry.key))
        }
        gesture = Gesture.PRESSED
    }

    override fun onMouseScroll(scrollDirection: UpOrDown, amount: Int): Boolean {
        scroll += if (scrollDirection == UpOrDown.UP) -WHEEL_STEP else WHEEL_STEP
        scroll = scroll.coerceIn(0, max(0, contentHeight + 4 - (area.h() - 2)))
        return true
    }

    override fun onKeyPressed(typedChar: Char, keyCode: Int): Interactable.Result {
        when {
            Interactable.isKeyComboCtrlA(keyCode) -> state.select(cards.map { it.entry.key })
            keyCode == Keyboard.KEY_Z && Interactable.hasControlDown() -> actions.undo()
            keyCode == Keyboard.KEY_DELETE -> actions.unbind()
            keyCode == Keyboard.KEY_F2 -> actions.rename()
            else -> return Interactable.Result.IGNORE
        }
        return Interactable.Result.SUCCESS
    }

    /** A tunnel to bring into sight the next time the list is drawn (it may not be laid out yet when asked). */
    var revealNext: P2PEntry? = null

    private fun reveal(entry: P2PEntry) {
        val cell = cards.firstOrNull { it.entry.key == entry.key } ?: return
        val visible = area.h() - 4
        if (cell.y < scroll) scroll = cell.y else if (cell.y + cell.h > scroll + visible) scroll = cell.y + cell.h - visible
    }

    // ---- drawing ----

    private fun color(entry: P2PEntry) = Palette.of(entry.frequency, state.meta(entry.frequency))

    private fun role(entry: P2PEntry): Pair<String, Int> = when {
        entry.frequency == 0L -> tr("etop2p.role.unbound") to Look.OFF_INK
        entry.output -> tr("etop2p.role.out") to Look.PRODUCT_INK
        else -> tr("etop2p.role.in") to Look.OK_INK
    }

    /** Room for the widest of the role words, so the titles of the rows line up. */
    private val roleWidth by lazy { listOf("unbound", "out", "in").maxOf { tr("etop2p.role.$it").guiWidth } }

    private fun distanceText(entry: P2PEntry): String {
        val distance = GameTexts.distance(entry)
        return if (distance == Double.MAX_VALUE) tr("etop2p.dim", entry.key.dim) else tr("etop2p.metres", distance.toInt())
    }

    private fun frame(x: Int, y: Int, w: Int, h: Int, picked: Boolean, lit: Boolean, drop: Boolean) {
        Look.rect(x, y, w, h, if (picked) 0xFF2C3F47.toInt() else if (lit) Look.TILE_HI else Look.TILE)
        when {
            drop -> Look.ring(x, y, w, h, 1, Look.GOLD)
            picked -> Look.ring(x, y, w, h, 1, Look.SELECTION)
        }
    }

    private fun drawCard(entry: P2PEntry, x: Int, y: Int, w: Int, lit: Boolean, drop: Boolean, z: Int) {
        val picked = entry.key in state.selected
        frame(x, y, w, CARD_H, picked, lit, drop)
        Look.rect(x + 1, y + 1, 3, CARD_H - 2, color(entry))
        Look.item(GameTexts.typeStack(entry.type), x + 7, y + 7, 16, z)
        val (roleText, roleInk) = role(entry)
        val textX = x + 27
        val attached = GameTexts.attachedStack(entry)
        // At the title's end: what it faces, and a dot when it is off. The line under it keeps its whole width.
        var right = x + w - 4
        if (attached != null) {
            Look.item(attached, right - 11, y + 3, 12, z)
            right -= 14
        }
        if (!entry.online) {
            Look.roundRect(right - 4, y + 7, 4, 4, 2, Look.RED_INK)
            right -= 7
        }
        Look.text(Listing.title(entry, GameTexts).fitTo(right - textX), textX, y + 5, if (entry.online) Look.INK else Look.MUTED)
        Look.text(roleText, textX, y + 17, roleInk)
        // The card says where; how many channels and the rest are a hover or a click away.
        val detail = "${entry.key.x}, ${entry.key.y}, ${entry.key.z} · ${distanceText(entry)}"
        val detailX = textX + roleText.guiWidth + 5
        Look.text(detail.fitTo(x + w - 4 - detailX), detailX, y + 17, Look.MUTED)
    }

    private fun drawRow(entry: P2PEntry, x: Int, y: Int, w: Int, lit: Boolean, drop: Boolean, z: Int) {
        val picked = entry.key in state.selected
        if (picked || lit || drop) frame(x, y, w, ROW_H, picked, lit, drop)
        Look.rect(x + 1, y + 2, 3, ROW_H - 4, color(entry))
        Look.item(GameTexts.typeStack(entry.type), x + 6, y + 1, 12, z)
        val (roleText, roleInk) = role(entry)
        Look.text(roleText, x + 22, y + 3, roleInk)
        val place = "${entry.key.x}, ${entry.key.y}, ${entry.key.z}"
        val distance = distanceText(entry)
        var right = x + w - 4
        Look.textRight(distance, right, y + 3, Look.MUTED)
        right -= 44
        if (w > 260) {
            Look.textRight(place, right, y + 3, Look.MUTED)
            right -= 92
        }
        if (!entry.online) {
            Look.textRight(tr("etop2p.offline"), right, y + 3, Look.RED_INK)
            right -= tr("etop2p.offline").guiWidth + 6
        }
        val titleX = x + 22 + roleWidth + 6
        val title = Listing.title(entry, GameTexts)
        val name = entry.name.takeIf { it.isNotEmpty() && it != title }
        Look.text(title.fitTo(right - titleX), titleX, y + 3, if (entry.online) Look.INK else Look.MUTED)
        if (name != null) {
            val nameX = titleX + title.guiWidth + 8
            if (right - nameX > 30) Look.text(name.fitTo(right - nameX), nameX, y + 3, Look.HINT)
        }
    }

    private fun drawHead(group: Group, x: Int, y: Int, w: Int, lit: Boolean, drop: Boolean) {
        Look.rect(x, y, w, HEAD_H, if (lit || drop) Look.KEY_HOVER else Look.KEY)
        if (drop) Look.ring(x, y, w, HEAD_H, 1, Look.GOLD)
        val open = group.frequency !in state.collapsed
        val cx = x + 7f
        val cy = y + HEAD_H / 2f
        if (open) Look.triangle(cx - 3, cy - 2, cx + 3, cy - 2, cx, cy + 2, Look.MUTED)
        else Look.triangle(cx - 2, cy - 3, cx + 2, cy, cx - 2, cy + 3, Look.MUTED)
        Look.roundRect(x + 14, y + 4, 8, 8, 2, Palette.of(group.frequency, group.meta))
        val title: String
        val detail: String
        if (group.bound) {
            title = group.name.ifEmpty { tr("etop2p.group.unnamed", Listing.frequencyCode(group.frequency)) }
            val inputs = if (group.input != null) 1 else 0
            detail = tr("etop2p.group.detail", GameTexts.typeName(group.members.first().type), inputs, group.outputs.size)
        } else {
            title = tr("etop2p.group.unbound")
            detail = tr("etop2p.group.unbound_detail", group.outputs.size)
        }
        var right = x + w - 4
        if (group.meta.note.isNotEmpty()) {
            Icons.COMMENT.draw(right - Icons.SIZE, y + 3, Look.MUTED)
            right -= Icons.SIZE + 4
        }
        if (group.meta.favorite) {
            Look.textRight("★", right, y + 4, Look.GOLD)
            right -= 12
        }
        if (group.bound && group.input == null) {
            val warn = tr("etop2p.group.no_input")
            Look.textRight(warn, right, y + 4, Look.AMBER_INK)
            right -= warn.guiWidth + 6
        }
        val titleX = x + 26
        val shownTitle = title.fitTo(right - titleX)
        Look.text(shownTitle, titleX, y + 4, Look.INK)
        val detailX = titleX + shownTitle.guiWidth + 8
        if (right - detailX > 40) Look.text(detail.fitTo(right - detailX), detailX, y + 4, Look.MUTED)
    }

    /** What a card has no room for; where it is and which way it goes the card says itself. */
    private fun tipFor(entry: P2PEntry): Tip {
        val meta = state.meta(entry.frequency)
        val title = Listing.title(entry, GameTexts)
        return Tip(title) {
            sub = GameTexts.typeName(entry.type).takeIf { it != title }
            if (entry.name.isNotEmpty() && entry.name != title) row(tr("etop2p.tip.connection"), entry.name)
            if (entry.frequency != 0L) {
                val members = state.connection(entry)
                row(tr("etop2p.tip.members"), tr("etop2p.tip.members_value", members.count { !it.output }, members.count { it.output }))
                row(tr("etop2p.tip.frequency"), Listing.frequencyCode(entry.frequency), Palette.of(entry.frequency, meta))
            }
            row(tr("etop2p.detail.side"), Actions.sideName(entry.key.side))
            if (entry.channels >= 0) row(tr("etop2p.tip.channels"), entry.channels.toString())
            if (!entry.online) row(tr("etop2p.tip.state"), tr("etop2p.offline"), Look.RED_INK)
            GameTexts.attachedStack(entry)?.let { icon(tr("etop2p.tip.attached", GameTexts.attachedName(entry)), item = it) }
            if (meta.note.isNotEmpty()) {
                gap()
                muted(meta.note)
            }
        }
    }
}
