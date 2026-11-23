package fyi.fyw.etolib.ui

import com.cleanroommc.modularui.api.widget.Interactable
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import com.cleanroommc.modularui.widget.Widget
import kotlin.math.max

/** A key's natural side. */
private const val SIDE = 16

/**
 * A square key with an [Icon] or a short text on it: [onClick] on a left click while [enabled], greyed and deaf while
 * not. [tip] (lines as [withTip] reads them) shows once the mouse rests on it. Its natural size is 16 by 16, wider only
 * for a text that needs it.
 *
 * ```
 * IconButton(Icons.GEAR, tip = "Settings") { openSettings() }
 * IconButton("+", tip = "Add", enabled = { canAdd }) { add() }
 * ```
 */
class IconButton private constructor(
    private val icon: Icon?,
    private val text: String?,
    tip: String?,
    private val enabled: () -> Boolean,
    private val onClick: () -> Unit,
) : Widget<IconButton>(), Interactable {

    /** A key showing [icon]. */
    constructor(icon: Icon, tip: String? = null, enabled: () -> Boolean = { true }, onClick: () -> Unit) :
        this(icon, null, tip, enabled, onClick)

    /** A key showing a short [text] ("+", "x2"). */
    constructor(text: String, tip: String? = null, enabled: () -> Boolean = { true }, onClick: () -> Unit) :
        this(null, text, tip, enabled, onClick)

    init {
        tip?.let { withTip(it) }
    }

    override fun getDefaultWidth(): Int = text?.let { max(SIDE, it.guiWidth + 6) } ?: SIDE

    override fun getDefaultHeight(): Int = SIDE

    override fun draw(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
        val w = area.w()
        val h = area.h()
        val on = enabled()
        Look.key(0, 0, w, h, on && isHovering)
        val ink = if (on) Look.INK else Look.OFF_INK
        when {
            icon != null -> icon.drawCentred(0, 0, w, h, ink)
            text != null -> Look.textCentered(text, w / 2f, (h - 8) / 2f, ink)
        }
    }

    override fun onMousePressed(mouseButton: Int): Interactable.Result = when {
        mouseButton != 0 -> Interactable.Result.IGNORE
        !enabled() -> Interactable.Result.STOP
        else -> {
            Look.click()
            onClick()
            Interactable.Result.SUCCESS
        }
    }
}
