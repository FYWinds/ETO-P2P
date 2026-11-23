package fyi.fyw.etolib.ui

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget

/**
 * A single-line text box in the kit's look: ModularUI's own text field, so the numpad and an IME type into it (it turns
 * lwjgl3ify's text input on while it has the keyboard), drawn as a dark face in a hard edge that turns gold while it is
 * written in. Give it a value and a size as any ModularUI text field.
 */
open class TextBox : TextFieldWidget() {

    init {
        setTextColor(Look.INK)
        setMarkedColor(Look.FIELD_SELECTION)
        hintColor(Look.HINT)
    }

    override fun drawBackground(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
        Look.field(0, 0, area.w(), area.h(), isFocused)
    }
}

/** A text box that takes the keyboard as soon as its popup opens, its text selected, so typing replaces it. */
internal open class OpeningField : TextBox() {

    private var opened = false

    override fun onUpdate() {
        super.onUpdate()
        if (!opened && isValid) {
            opened = true
            context.focus(this)
            handler.markAll()
        }
    }
}
