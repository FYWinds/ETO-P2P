package fyi.fyw.etolib.ui

import com.cleanroommc.modularui.api.widget.IWidget
import com.cleanroommc.modularui.screen.ModularScreen
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import net.minecraft.item.ItemStack
import net.minecraftforge.client.event.GuiScreenEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fluids.FluidStack
import org.lwjgl.opengl.GL11
import java.util.WeakHashMap
import kotlin.math.max
import kotlin.math.min

private const val PAD_X = 6
private const val PAD_Y = 5
private const val LINE = 10
private const val MAX_W = 220

/** An icon line's height, its icon's size, and where its text starts. */
private const val ICON_LINE = 12
private const val ICON_SIZE = 10
private const val ICON_IN = 14

/** An empty line between paragraphs. */
private const val GAP = 4

private const val ACTION_GAP = 10
private const val MOUSE_W = 7
private const val MOUSE_GAP = 4

/**
 * Gives this widget (any ModularUI widget, or a parent of the ones the mouse lands on) a tip, read from [text] as
 * [Tip.fromLines] reads lines, one per newline; empty text takes it away. It shows once the mouse has rested on the
 * widget for [Tip.WAIT_MS].
 */
fun <W : IWidget> W.withTip(text: String): W = apply { Tip.attach(this, if (text.isEmpty()) null else Tip.fromLines(text.split("\n"))) }

/** Gives this widget [tip], shown once the mouse has rested on it for [Tip.WAIT_MS]; null takes it away. */
fun <W : IWidget> W.withTip(tip: Tip?): W = apply { Tip.attach(this, tip) }

/**
 * A tooltip as Factory Flow draws one: a dark bevelled panel with a [title], a muted [sub]title, label and value rows,
 * notes (an item's or a fluid's icon may lead one), and under a rule what each mouse button does, each with a little
 * mouse that shows which. Written as a small DSL:
 *
 * ```
 * Tip("Iron Ingot") {
 *     sub = "Input"
 *     row("Rate", "20/s")
 *     say("Comes from the *north* bus")
 *     action(Tip.Input.LEFT, "Open it")
 * }
 * ```
 *
 * and shown one of three ways: [withTip] gives a ModularUI widget a tip shown once the mouse rests on it for [WAIT_MS];
 * [hover], called each frame from a widget that draws itself, does the same for the part under the mouse; [show] shows
 * a tip at the mouse this frame, at once. Tips are drawn after the whole screen, beside the mouse and kept on screen.
 * While a [Popup] is open only its own widgets' tips show, so nothing from below covers it.
 */
@UiDsl
class Tip(title: String?, build: Tip.() -> Unit = {}) {

    /** What the action is done with; the little mouse lights that part. */
    enum class Input {
        LEFT,
        RIGHT,
        WHEEL,
        MIDDLE,
        DRAG,

        /** A key, named in the action's label. */
        KEY,
    }

    private data class Row(val label: String, val value: String, val color: Int)

    /** A line of the body. */
    private sealed interface Note {

        /** Text in one colour; an empty one is a short gap. */
        data class Plain(val text: String, val color: Int) : Note

        /** Words between asterisks in gold, [gold] saying whether the line starts inside such a run (wrapped over). */
        data class Rich(val text: String, val color: Int, val gold: Boolean) : Note

        /** An item's or a fluid's icon, then rich text, on one line. */
        data class Led(val text: String, val item: ItemStack?, val fluid: FluidStack?) : Note

        /** A little room between paragraphs. */
        object Gap : Note
    }

    private data class Action(val input: Input, val label: String)

    /** The title over as many lines as it takes at [MAX_W]: a long one never runs out of the panel. */
    private val titleLines: List<String> = (title ?: "").let { if (it.guiWidth <= MAX_W) listOf(it) else wrapFormatted(it) }

    /** A muted line under the title. */
    var sub: String? = null

    var titleColor = TITLE

    private val rows = ArrayList<Row>()
    private val notes = ArrayList<Note>()
    private val actions = ArrayList<Action>()

    init {
        build()
    }

    /** A label at the left and its value at the right, the value in [color]. */
    fun row(label: String, value: String, color: Int = TITLE) {
        rows += Row(label, value, color)
    }

    /** Body text in [color], wrapped to the tip's width; formatting codes carry over the breaks. */
    fun note(text: String, color: Int = TEXT) {
        for (line in wrapFormatted(text)) notes += Note.Plain(line, color)
    }

    fun muted(text: String) = note(text, SUBTLE)

    /** A little room between paragraphs of the body. */
    fun gap() {
        notes += Note.Gap
    }

    /** Body text with the words that matter in gold: written between asterisks ("you *pinned* it"). */
    fun say(text: String, color: Int = TEXT) {
        var gold = false
        for (line in TextWrap.wrap(text, MAX_W) { richWidth(it) }) {
            if (line.isEmpty()) continue
            notes += Note.Rich(line, color, gold)
            for (ch in line) if (ch == '*') gold = !gold
        }
    }

    /** A line led by an item's or a fluid's icon, its text as [say] writes it, on one line. */
    fun icon(text: String, item: ItemStack? = null, fluid: FluidStack? = null) {
        notes += Note.Led(text, item, fluid)
    }

    /** What [input] does here, under the rule at the bottom. */
    fun action(input: Input, label: String) {
        actions += Action(input, label)
    }

    // region Layout

    private val Note.width: Int
        get() = when (this) {
            is Note.Plain -> text.guiWidth
            is Note.Rich -> richWidth(text)
            is Note.Led -> ICON_IN + richWidth(text)
            Note.Gap -> 0
        }

    private val Note.height: Int
        get() = when (this) {
            is Note.Plain -> if (text.isEmpty()) GAP else LINE
            is Note.Rich -> LINE
            is Note.Led -> ICON_LINE
            Note.Gap -> GAP
        }

    /** Actions laid out in lines no wider than [width]. */
    private fun actionLines(width: Int): List<List<Action>> {
        val out = ArrayList<List<Action>>()
        var line = ArrayList<Action>()
        var used = 0
        for (a in actions) {
            val w = a.width
            if (line.isNotEmpty() && used + ACTION_GAP + w > width) {
                out += line
                line = ArrayList()
                used = 0
            }
            used += (if (line.isEmpty()) 0 else ACTION_GAP) + w
            line += a
        }
        if (line.isNotEmpty()) out += line
        return out
    }

    private val contentWidth: Int
        get() {
            var w = titleLines.maxOf { it.guiWidth }
            sub?.let { w = max(w, it.guiWidth) }
            for (r in rows) w = max(w, r.label.guiWidth + 14 + r.value.guiWidth)
            for (n in notes) w = max(w, n.width)
            var actionsW = 0
            var widest = 0
            for (a in actions) {
                actionsW += (if (actionsW == 0) 0 else ACTION_GAP) + a.width
                widest = max(widest, a.width)
            }
            // Actions wrap rather than widen the panel past what the text needs, but never below one action.
            w = max(w, min(actionsW, max(widest, max(w, 150))))
            return min(max(w, 60), MAX_W + 40)
        }

    val width: Int
        get() = contentWidth + 2 * PAD_X

    val height: Int
        get() {
            var h = LINE * titleLines.size
            if (sub != null) h += LINE
            if (rows.isNotEmpty()) h += 3 + rows.size * LINE
            if (notes.isNotEmpty()) h += 3 + notes.sumOf { it.height }
            if (actions.isNotEmpty()) h += 5 + 4 + actionLines(contentWidth).size * 12 - 2
            return h + 2 * PAD_Y - 2
        }

    // endregion

    // region Drawing

    /**
     * Draws the tip beside the pointer, below and to the right, flipped left or up where it would cross [right] or
     * [bottom]. Returns {x, y, w, h} of where it went.
     */
    fun drawNear(mouseX: Int, mouseY: Int, right: Int, bottom: Int): IntArray {
        val w = width
        val h = height
        var x = mouseX + 12
        var y = mouseY + 12
        if (x + w > right) x = max(2, mouseX - 12 - w)
        if (y + h > bottom) y = max(2, bottom - h)
        draw(x, y)
        return intArrayOf(x, y, w, h)
    }

    /** Draws the tip with its top-left at [x], [y]. */
    fun draw(x: Int, y: Int) = Look.onTop {
        val cw = contentWidth
        chrome(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat())
        val left = x + PAD_X
        val right = left + cw
        var ty = y + PAD_Y
        for (line in titleLines) {
            Look.text(line, left, ty, titleColor)
            ty += LINE
        }
        sub?.let {
            Look.text(it, left, ty, SUBTLE)
            ty += LINE
        }
        if (rows.isNotEmpty()) {
            ty += 3
            for (r in rows) {
                Look.text(r.label, left, ty, SUBTLE)
                Look.textRight(r.value, right, ty, r.color)
                ty += LINE
            }
        }
        if (notes.isNotEmpty()) {
            ty += 3
            for (n in notes) {
                when (n) {
                    is Note.Plain -> Look.text(n.text, left, ty, n.color)
                    is Note.Rich -> drawRich(n.text, left.toFloat(), ty.toFloat(), n.color, n.gold)
                    is Note.Led -> {
                        Look.icon(n.item, n.fluid, left + 1f, ty.toFloat(), ICON_SIZE.toFloat())
                        // Item rendering leaves lighting and the depth test on, which dim and hide the flat colours.
                        GL11.glDisable(GL11.GL_LIGHTING)
                        GL11.glDisable(GL11.GL_DEPTH_TEST)
                        drawRich(n.text, (left + ICON_IN).toFloat(), (ty + 2).toFloat(), TEXT, false)
                    }
                    Note.Gap -> {}
                }
                ty += n.height
            }
        }
        if (actions.isNotEmpty()) {
            ty += 3
            Look.rect(left, ty, cw, 1, RULE)
            ty += 5
            for (line in actionLines(cw)) {
                var ax = left
                for (a in line) {
                    mouse(a.input, ax.toFloat(), ty.toFloat())
                    Look.text(a.label, ax + MOUSE_W + MOUSE_GAP, ty + 1, TEXT)
                    ax += a.width + ACTION_GAP
                }
                ty += 12
            }
        }
    }

    /** Shows this tip beside the mouse once this frame is drawn, over everything, at once. Call it each frame it shows. */
    fun show() {
        hook()
        asked = this
        askedAbout = null
        askedBuild = null
    }

    // endregion

    /**
     * Whether a tip may show: once the mouse has been on the same thing (`target`, compared by equals) for [WAIT_MS].
     * Moving about on it keeps the wait; leaving it, or nothing under the mouse, starts it again.
     */
    class Wait {

        private var on: Any? = null
        private var since = 0L

        fun ready(target: Any?): Boolean {
            val now = System.currentTimeMillis()
            if (target != on) {
                on = target
                since = now
            }
            return target != null && now - since >= WAIT_MS
        }

        /** How long the mouse has been on the thing it is on now, in ms. */
        val held: Long
            get() = System.currentTimeMillis() - since
    }

    companion object {

        const val BG = 0xFF26282D.toInt()
        const val EDGE = 0xFF111317.toInt()
        const val HI = 0xFF4A4C54.toInt()
        const val LO = 0xFF17191D.toInt()
        const val TEXT = 0xFFE5E5E5.toInt()
        const val TITLE = 0xFFF5F5F5.toInt()
        const val SUBTLE = 0xFFA3A3A3.toInt()
        const val RULE = 0xFF3A3B40.toInt()
        const val WARN = 0xFFFFD236.toInt()

        /** How long the mouse rests on a thing before its tip shows, in ms. */
        const val WAIT_MS = 800L

        /**
         * A tip from lines, the way plain tooltips are written: the first is the title, lines starting `§7` or `§8`
         * become muted notes, the rest notes in the body colour. Null for no lines.
         */
        fun fromLines(lines: List<String>): Tip? {
            if (lines.isEmpty()) return null
            return Tip(lines[0]) {
                for (line in lines.drop(1)) {
                    if (line.startsWith("§7") || line.startsWith("§8")) muted(line.substring(2)) else note(line)
                }
            }
        }

        /**
         * For a widget that draws itself: shows the tip [build] makes once the mouse has rested on [about] (a part of the
         * widget, or the widget) for [WAIT_MS]. Call it each frame the mouse is on that part; [build] runs only when the
         * tip shows.
         */
        fun hover(about: Any, build: () -> Tip?) {
            hook()
            asked = null
            askedAbout = about
            askedBuild = build
        }

        /** Factory Flow's tooltip panel: a hard dark edge, a lit top-left and a shadowed bottom-right, a soft drop shadow. */
        fun chrome(x: Float, y: Float, w: Float, h: Float) {
            Look.rect(x + 1, y + 2, w + 1, h + 1, 0x24000000)
            Look.rect(x + 2, y + 3, w, h, 0x18000000)
            Look.rect(x, y, w, h, EDGE)
            Look.rect(x + 1, y + 1, w - 2, h - 2, BG)
            Look.rect(x + 1, y + 1, w - 2, 1f, HI)
            Look.rect(x + 1, y + 1, 1f, h - 2, HI)
            Look.rect(x + 1, y + h - 2, w - 2, 1f, LO)
            Look.rect(x + w - 2, y + 1, 1f, h - 2, LO)
        }

        /** A mouse 7 by 10, the part that does the action lit; a key is a small keycap instead. */
        fun mouse(input: Input, x: Float, y: Float) {
            val line = SUBTLE
            val lit = TITLE
            if (input == Input.KEY) {
                Look.rect(x, y + 1, 7f, 7f, line)
                Look.rect(x + 1, y + 2, 5f, 5f, BG)
                Look.rect(x + 2, y + 3, 3f, 3f, lit)
                return
            }
            // Outline with clipped corners.
            Look.rect(x + 1, y, 5f, 1f, line)
            Look.rect(x + 1, y + 9, 5f, 1f, line)
            Look.rect(x, y + 1, 1f, 8f, line)
            Look.rect(x + 6, y + 1, 1f, 8f, line)
            // The line between the buttons, and the one under them.
            Look.rect(x + 3, y + 1, 1f, 3f, line)
            Look.rect(x + 1, y + 4, 5f, 1f, line)
            when (input) {
                Input.LEFT -> Look.rect(x + 1, y + 1, 2f, 3f, lit)
                Input.RIGHT -> Look.rect(x + 4, y + 1, 2f, 3f, lit)
                Input.WHEEL, Input.MIDDLE -> Look.rect(x + 3, y + 1, 1f, 3f, lit)
                Input.DRAG -> {
                    Look.rect(x + 1, y + 1, 2f, 3f, lit)
                    Look.rect(x + 2, y + 6, 3f, 1f, lit)
                    Look.rect(x + 3, y + 5, 1f, 3f, lit)
                }
                Input.KEY -> {}
            }
        }

        /** The tip each widget answers with while the mouse rests on it. */
        private val attached = WeakHashMap<IWidget, Tip>()

        /** The one wait every tip on the screen shares. */
        private val wait = Wait()

        /** What was asked to show this frame: a tip at once, or a tip to build once the mouse rests on a thing. */
        private var asked: Tip? = null
        private var askedAbout: Any? = null
        private var askedBuild: (() -> Tip?)? = null

        private var hooked = false

        /** Gives [widget] its tip, or takes it away with null. */
        internal fun attach(widget: IWidget, tip: Tip?) {
            hook()
            if (tip == null) attached.remove(widget) else attached[widget] = tip
        }

        private val Action.width: Int
            get() = MOUSE_W + MOUSE_GAP + label.guiWidth

        private fun richWidth(text: String): Int = text.replace("*", "").guiWidth

        /** [text] from [x], its runs between asterisks in gold. */
        private fun drawRich(text: String, x: Float, y: Float, color: Int, startGold: Boolean) {
            var gold = startGold
            var at = x
            for (run in text.split('*')) {
                if (run.isNotEmpty()) {
                    Look.text(run, at, y, if (gold) Look.GOLD else color)
                    at += run.guiWidth
                }
                gold = !gold
            }
        }

        /** [text] in lines at the tip's widest, as the game's font breaks formatted text. */
        @Suppress("UNCHECKED_CAST")
        private fun wrapFormatted(text: String): List<String> =
            Look.font.listFormattedStringToWidth(text, MAX_W) as List<String>

        private fun hook() {
            if (hooked) return
            hooked = true
            MinecraftForge.EVENT_BUS.register(Hook)
        }

        /** Draws what is to show this frame: what was asked for, or the tip of the widget the mouse rests on. */
        private fun drawFrame(mouseX: Int, mouseY: Int, right: Int, bottom: Int) {
            val tip = asked
            val about = askedAbout
            val build = askedBuild
            asked = null
            askedAbout = null
            askedBuild = null
            val screen = ModularScreen.getCurrent()
            val hovered: IWidget? = screen?.context?.hovered
            // A widget taken out of its tree since the hover was worked out has no panel to ask for: no tip this frame.
            if (hovered != null && !hovered.isValid) return
            // While a popup is open, only it explains itself: a tip from below would cover it.
            val top = screen?.panelManager?.topMostPanel
            val covered = top is Popup && hovered?.panel !== top
            when {
                tip != null -> if (!covered) tip.drawNear(mouseX, mouseY, right, bottom)
                about != null -> if (wait.ready(about) && !covered) build?.invoke()?.drawNear(mouseX, mouseY, right, bottom)
                else -> {
                    var owner = hovered
                    while (owner != null && owner !in attached) owner = if (owner.hasParent()) owner.parent else null
                    if (wait.ready(owner) && !covered) owner?.let { attached[it] }?.drawNear(mouseX, mouseY, right, bottom)
                }
            }
        }

        /** Draws the tips after the whole screen, its widgets and NEI's overlay included. */
        internal object Hook {

            @SubscribeEvent
            fun onDrawScreen(event: GuiScreenEvent.DrawScreenEvent.Post) {
                drawFrame(event.mouseX, event.mouseY, event.gui.width, event.gui.height)
            }
        }
    }
}
