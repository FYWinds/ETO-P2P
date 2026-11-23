package fyi.fyw.etolib.ui

import com.cleanroommc.modularui.api.IPanelHandler
import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.screen.SecondaryPanel
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import cpw.mods.fml.common.FMLCommonHandler
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.TickEvent
import org.lwjgl.input.Keyboard
import kotlin.math.roundToInt

/**
 * A floating panel in Factory Flow's menu style (bevelled with a hard drop shadow) that closes on any click outside it,
 * and on Escape. Opened at a screen point or centred on the screen, and kept on screen. [popupW] by [popupH] GUI pixels.
 */
open class Popup(name: String, val popupW: Int, val popupH: Int) : ModularPanel(uniqueName(name)) {

    /**
     * The key that opens this box, if one toggles it (see [toggled]): that key again closes it, and it never opens
     * twice.
     */
    var toggleKey: String? = null
        set(value) {
            field = value
            if (value != null) toggledBoxes[value] = this
        }

    /** Whether it has been put on screen (it opens a tick after it is asked to). */
    private var shown = false

    /** Whether it was closed before it could open, so it never does. */
    private var dismissed = false

    init {
        size(popupW, popupH)
    }

    /**
     * Opens it over [parent] with its top-left at the screen point [x], [y], nudged onto the screen. It opens on the
     * next tick: opened inside a click handler, the click that opened it would reach it as a click outside and close
     * it again. With no parent nothing opens.
     */
    fun open(parent: ModularPanel?, x: Int, y: Int) = openAt(parent) { x to y }

    /** Opens it over [parent] centred on the screen, [down] of the way down (a half centres it). */
    fun openCentred(parent: ModularPanel?, down: Float = 0.5f) = openAt(parent) {
        (Look.screenWidth - popupW) / 2 to ((Look.screenHeight - popupH) * down).roundToInt()
    }

    /** Opens it next tick where [at] says then: the screen is measured as it opens. */
    private fun openAt(parent: ModularPanel?, at: () -> Pair<Int, Int>) {
        if (parent == null) return
        Opener.next {
            if (!parent.isOpen || dismissed) return@next
            val (x, y) = at()
            pos(
                x.coerceIn(2, maxOf(2, Look.screenWidth - popupW - 2)),
                y.coerceIn(2, maxOf(2, Look.screenHeight - popupH - 2)),
            )
            shown = true
            IPanelHandler.simple(parent, SecondaryPanel.IPanelBuilder { _, _ -> this@Popup }, true).openPanel()
        }
    }

    /** Opens the waiting popups at the end of every client tick. */
    internal object Opener {

        /** What was asked to open since the last tick. */
        private val pending = ArrayList<() -> Unit>()
        private var registered = false

        /** Runs [open] at the end of this tick. */
        fun next(open: () -> Unit) {
            if (!registered) {
                registered = true
                FMLCommonHandler.instance().bus().register(this)
            }
            pending += open
        }

        @SubscribeEvent
        fun onClientTick(event: TickEvent.ClientTickEvent) {
            if (event.phase != TickEvent.Phase.END || pending.isEmpty()) return
            val now = ArrayList(pending)
            pending.clear()
            now.forEach { it() }
        }
    }

    /** Closes it, or keeps it from opening if it is still waiting to. */
    fun dismiss() {
        dismissed = true
        closeIfOpen()
    }

    /** A click off the popup dismisses it. */
    override fun closeOnOutOfBoundsClick(): Boolean {
        toggleKey?.let { clickedOff[it] = System.currentTimeMillis() }
        return true
    }

    override fun isDraggable(): Boolean = false

    override fun shouldAnimate(): Boolean = false

    override fun disablePanelsBelow(): Boolean = false

    /** Escape closes the popup alone, after whatever is written in it has had the key; the screen stays. */
    override fun onKeyPressed(typedChar: Char, keyCode: Int): Boolean {
        if (super.onKeyPressed(typedChar, keyCode)) return true
        if (keyCode != Keyboard.KEY_ESCAPE) return false
        closeIfOpen()
        return true
    }

    override fun drawBackground(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
        val w = area.w()
        val h = area.h()
        Look.rect(4, 4, w, h, 0x59000000)
        Look.rect(0, 0, w, h, Look.KEY_EDGE)
        Look.rect(1, 1, w - 2, h - 2, Look.MENU)
        Look.rect(1, 1, w - 2, 1, Look.HIGHLIGHT)
        Look.rect(1, 1, 1, h - 2, Look.HIGHLIGHT)
        Look.rect(1, h - 2, w - 2, 1, Look.SHADOW)
        Look.rect(w - 2, 1, 1, h - 2, Look.SHADOW)
    }

    companion object {

        /**
         * ModularUI keeps one handler per panel name for the life of the screen and re-opens the old panel when a name
         * comes back, so every popup gets a name of its own.
         */
        private var opened = 0

        private fun uniqueName(name: String): String = "${name}_${++opened}"

        /** Boxes opened from a key that toggles, by key name, and when a click off one last closed it. */
        private val toggledBoxes = HashMap<String, Popup>()
        private val clickedOff = HashMap<String, Long>()

        /**
         * Whether the key's press only closes (or waits on) its box: it is open, it is about to open, or the press that
         * landed just now was the click off it that closed it. False means open it.
         */
        fun toggled(key: String): Boolean {
            val off = clickedOff.remove(key)
            if (off != null && System.currentTimeMillis() - off < 250) return true
            val p = toggledBoxes[key] ?: return false
            return when {
                !p.shown && !p.dismissed -> true
                p.isOpen -> {
                    p.closeIfOpen()
                    true
                }
                else -> {
                    toggledBoxes.remove(key)
                    false
                }
            }
        }
    }
}
