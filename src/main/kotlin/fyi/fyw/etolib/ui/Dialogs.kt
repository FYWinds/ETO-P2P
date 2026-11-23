package fyi.fyw.etolib.ui

import com.cleanroommc.modularui.api.widget.Interactable
import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import com.cleanroommc.modularui.value.StringValue
import com.cleanroommc.modularui.widget.Widget
import org.lwjgl.input.Keyboard
import kotlin.math.max

private const val MIN_W = 200
private const val MAX_W = 280
private const val KEY_H = 16
private const val EDGE = 6

/** Where a dialog's body starts, under its title. */
private const val BODY_Y = 20

/** The OK key's face, lit under the mouse, and its letters. */
private const val PRIMARY = Look.SELECTION
private const val PRIMARY_HOT = 0xFF38E1F5.toInt()
private const val PRIMARY_INK = 0xFF0B1A1E.toInt()

/**
 * Small questions in popups centred on the screen, with Cancel and OK keys: Enter accepts, Escape or a click outside
 * cancels.
 */
object Dialogs {

    /**
     * Asks for a line of text under [title], starting from [initialText] with it all selected so typing replaces it.
     * [onAccept] hears the text on Enter or OK.
     */
    fun prompt(parent: ModularPanel, title: String, initialText: String, onAccept: (String) -> Unit) {
        val width = max(MIN_W, title.guiWidth + 2 * EDGE + 4)
        var text = initialText
        val keysY = BODY_Y + 16 + 8
        val popup = DialogPopup(width, keysY + KEY_H + EDGE) { onAccept(text) }
        val field = PromptField(enter = popup::accept, escape = popup::closeIfOpen).apply {
            value(StringValue.Dynamic({ text }, { text = it ?: "" }))
            autoUpdateOnChange(true)
        }
        popup.child(Label(listOf(title), Look.INK).pos(EDGE, EDGE).size(width - 2 * EDGE, 10))
        popup.child(field.pos(EDGE, BODY_Y).size(width - 2 * EDGE, 16))
        popup.addKeys(keysY)
        popup.openCentred(parent)
    }

    /** Asks [title], with [message] under it (wrapped); [onAccept] runs on Enter or OK. */
    fun confirm(parent: ModularPanel, title: String, message: String, onAccept: () -> Unit) {
        val width = (max(title.guiWidth, message.guiWidth) + 2 * EDGE).coerceIn(MIN_W, MAX_W)
        val lines = TextWrap.wrap(message, width - 2 * EDGE)
        val keysY = BODY_Y + lines.size * 10 + 8
        val popup = DialogPopup(width, keysY + KEY_H + EDGE, onAccept)
        popup.child(Label(listOf(title), Look.INK).pos(EDGE, EDGE).size(width - 2 * EDGE, 10))
        popup.child(Label(lines, Look.MUTED).pos(EDGE, BODY_Y).size(width - 2 * EDGE, lines.size * 10))
        popup.addKeys(keysY)
        popup.openCentred(parent)
    }
}

/**
 * A dialog's popup: [accept] closes it and runs [onAccept], once. Enter accepts when nothing written in it takes the
 * key first; Escape cancels, as in any popup.
 */
private class DialogPopup(width: Int, height: Int, private val onAccept: () -> Unit) :
    Popup("etolib_dialog", width, height) {

    private var done = false

    fun accept() {
        if (done) return
        done = true
        closeIfOpen()
        onAccept()
    }

    /** Cancel and OK at the bottom right, [y] down, OK last and lit. */
    fun addKeys(y: Int) {
        val ok = UiText.tr("etolib.ok")
        val cancel = UiText.tr("etolib.cancel")
        val okW = max(44, ok.guiWidth + 16)
        val cancelW = max(44, cancel.guiWidth + 16)
        child(DialogKey(ok, primary = true, action = this::accept).pos(popupW - EDGE - okW, y).size(okW, KEY_H))
        child(
            DialogKey(cancel, primary = false, action = this::closeIfOpen)
                .pos(popupW - EDGE - okW - 4 - cancelW, y)
                .size(cancelW, KEY_H),
        )
    }

    override fun onKeyPressed(typedChar: Char, keyCode: Int): Boolean {
        if (super.onKeyPressed(typedChar, keyCode)) return true
        if (keyCode != Keyboard.KEY_RETURN && keyCode != Keyboard.KEY_NUMPADENTER) return false
        accept()
        return true
    }
}

/** The prompt's text box: Enter accepts and Escape cancels while it is written in. */
private class PromptField(private val enter: () -> Unit, private val escape: () -> Unit) : OpeningField() {

    override fun onKeyPressed(character: Char, keyCode: Int): Interactable.Result {
        if (!isFocused) return super.onKeyPressed(character, keyCode)
        when (keyCode) {
            Keyboard.KEY_RETURN, Keyboard.KEY_NUMPADENTER -> enter()
            Keyboard.KEY_ESCAPE -> escape()
            else -> return super.onKeyPressed(character, keyCode)
        }
        return Interactable.Result.SUCCESS
    }
}

/** A dialog's key: OK lit in the selection colour with dark letters, Cancel in the plain key look. */
private class DialogKey(private val label: String, private val primary: Boolean, private val action: () -> Unit) :
    Widget<DialogKey>(), Interactable {

    override fun draw(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
        val w = area.w()
        val h = area.h()
        val textY = (h - 8) / 2f
        if (primary) {
            Look.rect(0, 0, w, h, if (isHovering) PRIMARY_HOT else PRIMARY)
            Look.text(label, (w - label.guiWidth) / 2f, textY, PRIMARY_INK, shadow = false)
        } else {
            Look.key(0, 0, w, h, isHovering)
            Look.textCentered(label, w / 2f, textY, Look.INK)
        }
    }

    override fun onMousePressed(mouseButton: Int): Interactable.Result {
        if (mouseButton != 0) return Interactable.Result.IGNORE
        Look.click()
        action()
        return Interactable.Result.SUCCESS
    }
}
