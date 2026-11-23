package fyi.fyw.etolib.ui

import com.cleanroommc.modularui.ModularUI
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import me.eigenraven.lwjgl3ify.api.InputEvents
import net.minecraft.client.Minecraft
import net.minecraft.util.ChatAllowedCharacters
import net.minecraftforge.client.event.GuiOpenEvent
import net.minecraftforge.common.MinecraftForge

/**
 * The game's text input, for one of a screen's own text boxes while it is being written in (ModularUI's text fields
 * already do this for themselves). With lwjgl3ify (the pack's Java 17+ game) typed text comes only while text input is
 * on, as text events of its own: the numpad's digits, an IME's words. A key press then carries a character only for
 * some keys, and that one comes again as text. So a box turns text input [on] when writing starts and [off] when it
 * ends, takes its text from `ModernInteractable.onTextInput` (marked `@Optional.Method(modid = "lwjgl3ify")`), and
 * types a key's character only when [keyTypes] says so. Without lwjgl3ify none of this does anything and the keys carry
 * the text, as always.
 */
class TextInput {

    /** The [screens] count when this box turned text input on; -1 while it has not. */
    private var held = -1

    /** Turns text input on for this box (again, if a change of screen turned it off). */
    fun on() {
        if (held == screens) return
        held = screens
        if (events) InputEvents.beginTextInput()
    }

    /** Turns it off again, if this box turned it on and no change of screen has since. */
    fun off() {
        if (held < 0) return
        val live = held == screens
        held = -1
        if (live && events) InputEvents.endTextInput()
    }

    /**
     * For a key with a character, pressed while this box is written in: whether that character is typed. Without
     * lwjgl3ify, always. With it, not while text input is on, as the text comes as a text event too; but yes when a
     * change of screen turned it off (text input goes on again here, for the keys after).
     */
    fun keyTypes(): Boolean {
        if (!events) return true
        if (held == screens) return false
        if (held >= 0) on()
        return true
    }

    companion object {

        /** How many times the game has shown another screen: lwjgl3ify turns text input off then, whoever had it on. */
        private var screens = 0

        /** Whether lwjgl3ify is in the game, so text comes as text events; from then on the screens shown are counted. */
        private val events: Boolean = ModularUI.Mods.LWJGL3IFY.isLoaded().also {
            if (it) MinecraftForge.EVENT_BUS.register(Screens)
        }

        /** What of [text] may be written in a box, as the chat takes it (no control characters, no §). */
        fun printable(text: String): String {
            val b = StringBuilder(text.length)
            for (c in text) if (ChatAllowedCharacters.isAllowedCharacter(c)) b.append(c)
            return b.toString()
        }

        /** Counts the screens shown, as lwjgl3ify resets its text input: on every change of screen. */
        internal object Screens {

            @SubscribeEvent
            fun onGuiOpen(event: GuiOpenEvent) {
                if (Minecraft.getMinecraft().currentScreen !== event.gui) screens++
            }
        }
    }
}
