package fyi.fyw.etolib.ui

import com.cleanroommc.modularui.api.widget.Interactable
import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import com.cleanroommc.modularui.widget.Widget
import net.minecraft.item.ItemStack
import org.lwjgl.input.Keyboard
import kotlin.math.max

/** An entry's height, a separator's, and the padding between the popup's edge and its entries. */
private const val ROW = 18
private const val SEP = 7
private const val PAD = 3
private const val MIN_W = 60

/**
 * A context menu in the popup look: entries with an optional icon, a label, a hint at the right (a shortcut, a count),
 * greyed when disabled, ticked when checked, separators between groups, and submenus that open beside their entry when
 * the mouse comes onto it or clicks it (one level deep). Choosing an entry runs its action and closes the whole menu;
 * Escape or a click outside it closes it too.
 *
 * ```
 * Menu.open(panel, mouseX, mouseY) {
 *     entry("Rename", hint = "F2", icon = Icons.PENCIL) { rename() }
 *     entry("Delete", enabled = canDelete) { delete() }
 *     separator()
 *     submenu("Sort by") {
 *         entry("Name", checked = sort == Sort.NAME) { sort = Sort.NAME }
 *         entry("Count", checked = sort == Sort.COUNT) { sort = Sort.COUNT }
 *     }
 * }
 * ```
 */
object Menu {

    /** Anything a menu lists. */
    sealed interface Item

    /** What a submenu may list: entries and separators. */
    sealed interface Leaf : Item

    /**
     * An entry: [item] or [icon] leads it (the item wins when both are given), [hint] sits at its right; a disabled
     * entry is greyed and does nothing; [checked] puts a tick before it.
     */
    data class Entry(
        val label: String,
        val hint: String = "",
        val item: ItemStack? = null,
        val icon: Icon? = null,
        val enabled: Boolean = true,
        val checked: Boolean = false,
        val action: () -> Unit,
    ) : Leaf

    /** An entry that opens [entries] beside itself instead of acting. */
    data class Submenu(
        val label: String,
        val entries: List<Leaf>,
        val item: ItemStack? = null,
        val icon: Icon? = null,
        val enabled: Boolean = true,
    ) : Item

    /** A line between groups of entries. */
    object Separator : Leaf

    /** Assembles a submenu: entries and separators. */
    @UiDsl
    open class LeafBuilder internal constructor() {

        internal val items = ArrayList<Item>()

        fun entry(
            label: String,
            hint: String = "",
            item: ItemStack? = null,
            icon: Icon? = null,
            enabled: Boolean = true,
            checked: Boolean = false,
            action: () -> Unit,
        ) {
            items += Entry(label, hint, item, icon, enabled, checked, action)
        }

        fun separator() {
            items += Separator
        }
    }

    /** Assembles a menu: entries, separators and submenus. */
    class Builder internal constructor() : LeafBuilder() {

        fun submenu(
            label: String,
            item: ItemStack? = null,
            icon: Icon? = null,
            enabled: Boolean = true,
            build: LeafBuilder.() -> Unit,
        ) {
            items += Submenu(label, LeafBuilder().apply(build).items.filterIsInstance<Leaf>(), item, icon, enabled)
        }
    }

    /** Opens a menu of [entries] over [parent] with its top-left at the screen point [x], [y] (kept on screen). */
    fun open(parent: ModularPanel, x: Int, y: Int, entries: List<Item>) {
        MenuPopup(null, MenuList(entries, nested = false)).open(parent, x, y)
    }

    /** Opens the menu [build] makes over [parent] with its top-left at the screen point [x], [y] (kept on screen). */
    fun open(parent: ModularPanel, x: Int, y: Int, build: Builder.() -> Unit) =
        open(parent, x, y, Builder().apply(build).items)
}

/**
 * A menu's popup. A submenu's knows its [root]: a click on the root leaves the submenu to the root's entries, a click
 * anywhere else closes both, and Escape in either closes both.
 */
private class MenuPopup(val root: MenuPopup?, list: MenuList) :
    Popup("etolib_menu", list.wide + 2 * PAD, list.tall + 2 * PAD) {

    init {
        list.popup = this
        child(list.pos(PAD, PAD))
    }

    override fun closeOnOutOfBoundsClick(): Boolean {
        val r = root ?: return super.closeOnOutOfBoundsClick()
        if (r.area.isInside(context.absMouseX, context.absMouseY)) return false
        r.closeIfOpen()
        return true
    }

    override fun onKeyPressed(typedChar: Char, keyCode: Int): Boolean {
        if (keyCode != Keyboard.KEY_ESCAPE) return super.onKeyPressed(typedChar, keyCode)
        (root ?: this).closeIfOpen()
        return true
    }
}

/** What an entry draws: its label, what leads it, and how it reads. */
private val Menu.Item.label: String
    get() = when (this) {
        is Menu.Entry -> label
        is Menu.Submenu -> label
        Menu.Separator -> ""
    }

private val Menu.Item.leadItem: ItemStack?
    get() = when (this) {
        is Menu.Entry -> item
        is Menu.Submenu -> item
        Menu.Separator -> null
    }

private val Menu.Item.leadIcon: Icon?
    get() = when (this) {
        is Menu.Entry -> icon
        is Menu.Submenu -> icon
        Menu.Separator -> null
    }

private val Menu.Item.enabled: Boolean
    get() = when (this) {
        is Menu.Entry -> enabled
        is Menu.Submenu -> enabled
        Menu.Separator -> false
    }

/** The entries of a menu, drawn and picked; a top-level list also opens its submenus. */
private class MenuList(private val entries: List<Menu.Item>, private val nested: Boolean) :
    Widget<MenuList>(), Interactable {

    lateinit var popup: MenuPopup

    /** Where each entry starts, down from the list's top. */
    private val tops = IntArray(entries.size)

    /** The widths of the columns before the label (a tick, an icon) and after it (a submenu's arrow); 0 when unused. */
    private val tickCol = if (entries.any { it is Menu.Entry && it.checked }) 12 else 0
    private val iconCol = if (entries.any { it.leadItem != null || it.leadIcon != null }) 20 else 0
    private val arrowCol = if (!nested && entries.any { it is Menu.Submenu }) 12 else 0

    val tall: Int
    val wide: Int

    /** The submenu open now, and the entry it belongs to (-1 for none). */
    private var open: MenuPopup? = null
    private var openFor = -1

    init {
        var y = 0
        entries.forEachIndexed { i, e ->
            tops[i] = y
            y += heightOf(e)
        }
        tall = y
        val labelW = entries.maxOfOrNull { it.label.guiWidth } ?: 0
        val hintW = entries.maxOfOrNull { (it as? Menu.Entry)?.hint?.takeIf(String::isNotEmpty)?.guiWidth ?: 0 } ?: 0
        val want = 6 + tickCol + iconCol + labelW + (if (hintW > 0) 14 + hintW else 0) + arrowCol + 6
        wide = want.coerceIn(MIN_W, max(MIN_W, Look.screenWidth - 2 * PAD - 8))
        size(wide, tall)
    }

    private fun heightOf(e: Menu.Item): Int = if (e === Menu.Separator) SEP else ROW

    /** The entry under the mouse, or -1. */
    private fun hovered(): Int {
        if (!isHovering) return -1
        val my = context.absMouseY - area.y()
        return entries.indices.firstOrNull { my >= tops[it] && my < tops[it] + heightOf(entries[it]) } ?: -1
    }

    override fun draw(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
        val hovered = hovered()
        if (!nested) follow(hovered)
        val w = area.w()
        val z = context.currentDrawingZ
        entries.forEachIndexed { i, e ->
            val y = tops[i]
            if (e === Menu.Separator) {
                Look.rect(4, y + SEP / 2, w - 8, 1, Look.RULE)
                return@forEachIndexed
            }
            val lit = e.enabled && (i == hovered || i == openFor)
            if (lit) Look.rect(0, y, w, ROW, Look.MENU_HOVER)
            val ink = if (e.enabled) Look.INK else Look.OFF_INK
            val iconY = y + (ROW - 1 - Icons.SIZE) / 2
            val textY = y + (ROW - 8) / 2
            var x = 6
            if (tickCol > 0) {
                if (e is Menu.Entry && e.checked) Icons.CHECK.draw(x, iconY, ink)
                x += tickCol
            }
            if (iconCol > 0) {
                val stack = e.leadItem
                if (stack != null) Look.item(stack, x, y + 1, 16, z) else e.leadIcon?.draw(x + 3, iconY, ink)
                x += iconCol
            }
            val right = w - 6 - arrowCol
            val label = e.label.fitTo(right - x)
            Look.text(label, x, textY, ink)
            if (e is Menu.Entry && e.hint.isNotEmpty()) {
                val room = right - (x + label.guiWidth + 14)
                if (room > 0) Look.textRight(e.hint.fitTo(room), right, textY, if (e.enabled) Look.MUTED else Look.OFF_INK)
            }
            if (e is Menu.Submenu && arrowCol > 0) {
                Icons.ARROW.draw(w - 3 - Icons.SIZE, iconY, if (lit) Look.INK else Look.MUTED)
            }
        }
    }

    /** Opens the submenu of the entry the mouse came onto, and closes the open one when the mouse moves to another. */
    private fun follow(hovered: Int) {
        val e = entries.getOrNull(hovered) ?: return
        when {
            e === Menu.Separator -> {}
            e is Menu.Submenu && e.enabled -> if (openFor != hovered) openSub(hovered, e)
            openFor >= 0 -> closeSub()
        }
    }

    /** Opens [sub], entry [i]'s submenu, beside it: to the right, or to the left where the screen ends. */
    private fun openSub(i: Int, sub: Menu.Submenu) {
        closeSub()
        val box = MenuPopup(popup, MenuList(sub.entries, nested = true))
        val pa = popup.area
        val x = (pa.x() + pa.w() - 2).let { if (it + box.popupW > Look.screenWidth - 2) pa.x() - box.popupW + 2 else it }
        box.open(popup, x, area.y() + tops[i] - PAD)
        open = box
        openFor = i
    }

    private fun closeSub() {
        open?.dismiss()
        open = null
        openFor = -1
    }

    override fun onMousePressed(mouseButton: Int): Interactable.Result {
        if (mouseButton != 0) return Interactable.Result.IGNORE
        val i = hovered()
        val e = entries.getOrNull(i) ?: return Interactable.Result.IGNORE
        if (!e.enabled) return Interactable.Result.SUCCESS
        when (e) {
            is Menu.Submenu -> if (!nested && openFor != i) openSub(i, e)
            is Menu.Entry -> {
                Look.click()
                (popup.root ?: popup).closeIfOpen()
                e.action()
            }
            Menu.Separator -> {}
        }
        return Interactable.Result.SUCCESS
    }
}
