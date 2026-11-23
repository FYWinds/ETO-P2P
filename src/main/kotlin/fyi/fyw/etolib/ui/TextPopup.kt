package fyi.fyw.etolib.ui

import com.cleanroommc.modularui.value.StringValue
import kotlin.math.max

/**
 * A titled text box that takes focus with its text selected; Enter or clicking away commits, and so does Escape (with
 * the text it opened with instead, when ModularUI's "ESC restores last text" setting is on).
 */
object TextPopup {

    /** The popup, ready for [Popup.open]: [commit] hears the text once, as the box lets go of the keyboard. */
    fun create(title: String, current: String, commit: (String) -> Unit): Popup {
        val width = max(160, title.guiWidth + 16)
        var text = current
        val popup = Popup("etolib_text", width, 44)
        val field = OpeningField().apply {
            value(
                StringValue.Dynamic({ text }, { typed ->
                    text = typed ?: ""
                    commit(text)
                    popup.closeIfOpen()
                }),
            )
        }
        return popup.apply {
            child(Label(listOf(title), Look.MUTED).pos(6, 6).size(width - 12, 10))
            child(field.pos(6, 20).size(width - 12, 16))
        }
    }
}
