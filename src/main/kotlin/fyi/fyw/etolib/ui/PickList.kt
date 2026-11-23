package fyi.fyw.etolib.ui

import com.cleanroommc.modularui.api.UpOrDown
import com.cleanroommc.modularui.api.widget.IWidget
import com.cleanroommc.modularui.api.widget.Interactable
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import com.cleanroommc.modularui.widget.Widget
import fyi.fyw.etolib.text.TextMatch
import net.minecraft.item.ItemStack
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private const val MAX_ROWS = 10

/**
 * Rows of icon, label and detail; click one to pick it and close the popup. The current choice is shaded, the hovered
 * row lit; the wheel scrolls when there are more rows than fit. For action lists and dropdowns; build one with
 * [PickList.popup].
 */
class PickList private constructor(private val rows: List<Row>, width: Int, maxRows: Int) :
    Widget<PickList>(), Interactable {

    /** A row of the list. */
    sealed interface Row

    /**
     * A row that does something: [lead] before its [label] (an item, a coloured dot, a plus), a [detail] at its right,
     * shaded when it is the [current] choice. The filter finds it by its label, or by its [search] words.
     */
    data class Entry(
        val label: String,
        val detail: String = "",
        val lead: Lead? = null,
        val labelColor: Int = Look.INK,
        val current: Boolean = false,
        val search: String = "",
        val action: () -> Unit,
    ) : Row

    /** A line between groups of rows: it does nothing, and a filter leaves it out. */
    object Rule : Row

    /** What leads a row's label. */
    sealed interface Lead {

        /** An item's icon. */
        data class Stack(val stack: ItemStack) : Lead

        /** A dot of [colour], as a status shows. */
        data class Dot(val colour: Int) : Lead

        /** A plus: the row makes something new. */
        object Plus : Lead
    }

    /** Assembles a pick list's rows. */
    @UiDsl
    class Builder internal constructor() {

        internal val rows = ArrayList<Row>()

        fun entry(
            label: String,
            detail: String = "",
            lead: Lead? = null,
            labelColor: Int = Look.INK,
            current: Boolean = false,
            search: String = "",
            action: () -> Unit,
        ) {
            rows += Entry(label, detail, lead, labelColor, current, search, action)
        }

        fun rule() {
            rows += Rule
        }
    }

    private val visibleRows = max(1, min(maxRows, rows.size))
    private var shown: List<Row> = rows
    private var scroll = 0

    /** Told which row the mouse is on; see [popup]. */
    private var onHover: ((Int) -> Unit)? = null

    init {
        size(width, visibleRows * ROW)
        // Opened scrolled to the (last) current choice when it would be out of sight.
        val current = rows.indexOfLast { it is Entry && it.current }
        if (current >= visibleRows) scroll = min(current, rows.size - visibleRows)
    }

    override fun dispose() {
        onHover?.invoke(-1)
        super.dispose()
    }

    private fun setFilter(text: String) {
        val f = text.lowercase(Locale.ROOT)
        shown = if (f.isEmpty()) rows else rows.filter {
            it is Entry && (TextMatch.contains(it.label.lowercase(Locale.ROOT), f) || TextMatch.contains(it.search.lowercase(Locale.ROOT), f))
        }
        scroll = 0
    }

    /** The row under the mouse, counted in [shown], or -1. */
    private fun hoveredRow(): Int {
        if (!isHovering) return -1
        val my = context.absMouseY - area.y()
        val row = my / ROW
        return if (my >= 0 && row < visibleRows) row + scroll else -1
    }

    override fun draw(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
        val w = area.w()
        val hovered = hoveredRow()
        // Told every frame the list is drawn, so the listener can tell a closed list (no word for a while).
        onHover?.invoke(if (hovered in shown.indices) rows.indexOf(shown[hovered]) else -1)
        val z = context.currentDrawingZ
        for (r in 0 until visibleRows) {
            val i = r + scroll
            if (i >= shown.size) break
            val y = r * ROW
            val e = when (val row = shown[i]) {
                Rule -> {
                    Look.rect(4, y + ROW / 2, w - 8, 1, Look.RULE)
                    continue
                }
                is Entry -> row
            }
            when {
                i == hovered -> Look.rect(0, y, w, ROW, Look.MENU_HOVER)
                e.current -> Look.rect(0, y, w, ROW, Look.TILE)
            }
            val x = 4 + when (val lead = e.lead) {
                is Lead.Stack -> {
                    Look.item(lead.stack, 4, y + 2, 16, z)
                    20
                }
                Lead.Plus -> {
                    Icons.PLUS.draw(4, y + 5, Look.MUTED)
                    14
                }
                is Lead.Dot -> {
                    Look.roundRect(6, y + 7, 6, 6, 3, lead.colour)
                    14
                }
                null -> 0
            }
            // The label says what the row does, so it is never cut for the detail: a detail with no room is left out.
            val label = e.label.fitTo(w - x - 4)
            val room = w - 4 - (x + label.guiWidth + 8)
            Look.text(label, x, y + 6, e.labelColor)
            if (e.detail.isNotEmpty() && room >= 30) Look.textRight(e.detail.fitTo(room), w - 4, y + 6, Look.MUTED)
        }
        if (shown.size > visibleRows) {
            val barH = max(8, visibleRows * ROW * visibleRows / shown.size)
            val barY = (visibleRows * ROW - barH) * scroll / max(1, shown.size - visibleRows)
            Look.rect(w - 2, barY, 2, barH, Look.MUTED)
        }
    }

    override fun onMousePressed(mouseButton: Int): Interactable.Result {
        val i = hoveredRow()
        if (mouseButton != 0 || i !in shown.indices) return Interactable.Result.IGNORE
        val e = shown[i] as? Entry ?: return Interactable.Result.SUCCESS
        Look.click()
        panel.closeIfOpen()
        e.action()
        return Interactable.Result.SUCCESS
    }

    override fun onMouseScroll(scrollDirection: UpOrDown, amount: Int): Boolean {
        val most = max(0, shown.size - visibleRows)
        scroll = (scroll + if (scrollDirection == UpOrDown.UP) -1 else 1).coerceIn(0, most)
        return true
    }

    companion object {

        const val ROW = 20

        /**
         * A popup listing the rows [build] makes under an optional [title] and filter box, as wide as its widest row
         * (at least [minWidth], at most the screen), showing up to [maxRows] rows before it scrolls. [onHover] hears
         * which row the mouse is on (its index among the rows built, -1 for none) every frame, and -1 once more when
         * the popup closes: for showing elsewhere what a row would do. Open it with [Popup.open].
         *
         * ```
         * PickList.popup(title = "Sort by") {
         *     entry("Name", current = sort == NAME) { sort = NAME }
         *     rule()
         *     entry("New group", lead = PickList.Lead.Plus) { addGroup() }
         * }.open(panel, x, y)
         * ```
         */
        fun popup(
            title: String? = null,
            filterable: Boolean = false,
            minWidth: Int = 0,
            maxRows: Int = MAX_ROWS,
            onHover: ((Int) -> Unit)? = null,
            build: Builder.() -> Unit,
        ): Popup {
            val rows = Builder().apply(build).rows
            // Never cut a row: grow to the widest label and detail (the game font has one size).
            var width = max(minWidth, title?.let { it.guiWidth + 16 } ?: 0)
            for (e in rows) {
                if (e !is Entry) continue
                val detail = if (e.detail.isEmpty()) 0 else e.detail.guiWidth + 12
                val lead = when (e.lead) {
                    is Lead.Stack -> 26
                    null -> 6
                    else -> 18
                }
                width = max(width, lead + e.label.guiWidth + detail + 18)
            }
            // On the screen: narrower drops a row's detail rather than cut its label; shorter scrolls.
            width = min(width, min(360, Look.screenWidth - 4))
            val above = 8 + (if (title == null) 0 else 14) + (if (filterable) 18 else 0)
            val fits = max(2, (Look.screenHeight - 4 - above) / ROW)
            val list = PickList(rows, width - 8, min(maxRows, fits))
            list.onHover = onHover
            var y = 4
            val top = buildList<IWidget> {
                title?.let {
                    add(Label(listOf(it), Look.MUTED).pos(6, y + 2).size(width - 12, 10))
                    y += 14
                }
                if (filterable) {
                    add(SearchField(UiText.tr("etolib.filter")) { list.setFilter(it) }.pos(4, y).size(width - 8, 14))
                    y += 18
                }
            }
            return Popup("etolib_pick", width, y + list.visibleRows * ROW + 4).apply {
                top.forEach { child(it) }
                child(list.pos(4, y))
            }
        }
    }
}
