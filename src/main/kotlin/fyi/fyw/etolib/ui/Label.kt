package fyi.fyw.etolib.ui

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import com.cleanroommc.modularui.widget.Widget

/** Lines of text in one colour, a line every 10 pixels, each cut to the width it is given: a popup's title or message. */
internal class Label(private val lines: List<String>, private val color: Int) : Widget<Label>() {

    override fun draw(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
        val w = area.w()
        lines.forEachIndexed { i, line -> Look.text(line.fitTo(w), 0, i * 10, color) }
    }

    /** Only words: the mouse goes through to what is under it. */
    override fun canHover(): Boolean = false
}
