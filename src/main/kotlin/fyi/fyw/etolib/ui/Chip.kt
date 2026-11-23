package fyi.fyw.etolib.ui

import com.cleanroommc.modularui.api.widget.Interactable
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import com.cleanroommc.modularui.widget.Widget

/** Room either side of a chip's content, and between its icon and its label. */
private const val PAD = 6
private const val GAP = 4

/**
 * A small toggle: a pill with an optional [icon] and a [label], picked out in the selection colour while [selected]
 * says so, [onClick] on a left click. [tip] (lines as [withTip] reads them) shows once the mouse rests on it. Its
 * natural size fits the label, 16 high.
 *
 * ```
 * Chip("Inputs", tip = "Show input tunnels", selected = { filter.inputs }) { filter.inputs = !filter.inputs }
 * ```
 */
class Chip(
    private val label: String,
    private val icon: Icon? = null,
    tip: String? = null,
    private val selected: () -> Boolean,
    private val onClick: () -> Unit,
) : Widget<Chip>(), Interactable {

    init {
        tip?.let { withTip(it) }
    }

    override fun getDefaultWidth(): Int = PAD + (if (icon != null) Icons.SIZE + GAP else 0) + label.guiWidth + PAD

    override fun getDefaultHeight(): Int = 16

    override fun draw(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
        val w = area.w()
        val h = area.h()
        val on = selected()
        val hot = isHovering
        Look.chip(0, 0, w, h, Look.SELECTION, on, hot)
        val ink = if (on || hot) Look.INK else Look.MUTED
        var x = PAD
        icon?.let {
            it.draw(x, (h - 1 - Icons.SIZE) / 2, if (on) Look.SELECTION else ink)
            x += Icons.SIZE + GAP
        }
        Look.text(label.fitTo(w - x - PAD + 2), x, (h - 8) / 2, ink)
    }

    override fun onMousePressed(mouseButton: Int): Interactable.Result {
        if (mouseButton != 0) return Interactable.Result.IGNORE
        Look.click()
        onClick()
        return Interactable.Result.SUCCESS
    }
}
