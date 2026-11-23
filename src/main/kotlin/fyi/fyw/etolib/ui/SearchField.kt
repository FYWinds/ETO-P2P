package fyi.fyw.etolib.ui

import com.cleanroommc.modularui.api.widget.Interactable
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.value.StringValue

/** Room at the left for the magnifier, and at the right for the cross. */
private const val LEFT = 15
private const val RIGHT = 12

/**
 * A search box: a magnifier, the [placeholder] while it is empty, and a cross that clears it while it is not. Every
 * change to the text, typed or cleared, reaches [onChange] as it happens. Being a [TextBox], the numpad and an IME type
 * into it. Size it as any widget (16 high suits it; 14 at least).
 */
class SearchField(
    placeholder: String = UiText.tr("etolib.search"),
    initial: String = "",
    private val onChange: (String) -> Unit,
) : TextBox() {

    private var query = initial

    init {
        value(StringValue.Dynamic({ query }, { changed(it ?: "") }))
        autoUpdateOnChange(true)
        hintText(placeholder)
        padding(LEFT, RIGHT, 0, 0)
    }

    private fun changed(text: String) {
        if (text == query) return
        query = text
        onChange(text)
    }

    /** Whether the mouse, at [mx], [my] in the box, is on the cross (shown while there is text). */
    private fun overClear(mx: Int, my: Int): Boolean =
        query.isNotEmpty() && mx >= area.w() - RIGHT && mx < area.w() && my >= 0 && my < area.h()

    override fun onMousePressed(mouseButton: Int): Interactable.Result {
        if (mouseButton == 0 && isHovering && overClear(context.mouseX, context.mouseY)) {
            handler.clear()
            changed("")
            return Interactable.Result.SUCCESS
        }
        return super.onMousePressed(mouseButton)
    }

    /** The magnifier and the cross go over the text, which scrolls under the box's edges as it runs long. */
    override fun postDraw(context: ModularGuiContext, transformed: Boolean) {
        super.postDraw(context, transformed)
        if (transformed) return
        val w = area.w()
        val h = area.h()
        val iy = (h - Icons.SIZE) / 2
        Look.rect(1, 1, LEFT - 2, h - 2, Look.FIELD)
        Icons.SEARCH.draw(4, iy, if (isFocused) Look.INK else Look.MUTED)
        if (query.isEmpty()) return
        Look.rect(w - RIGHT, 1, RIGHT - 1, h - 2, Look.FIELD)
        val hot = isHovering && overClear(context.mouseX, context.mouseY)
        Icons.CROSS.draw(w - RIGHT + 1, iy, if (hot) Look.INK else Look.MUTED)
    }
}
