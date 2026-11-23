package fyi.fyw.etop2p.dev

import cpw.mods.fml.common.FMLCommonHandler
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.TickEvent
import fyi.fyw.etop2p.EtoP2P
import fyi.fyw.etop2p.client.ClientState
import fyi.fyw.etop2p.client.ScreenState
import java.io.File
import java.nio.ByteBuffer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.settings.KeyBinding

/**
 * For testing the screen without hands, only with `-Detop2p.dev=true` on the client: mouse and key events put
 * straight into the game's own input queue, so they arrive whether or not the window has the system's focus. A test
 * writes `etop2p-dev-input.txt` into the game directory; its lines are carried out one per tick and the file is
 * deleted when it has been read. When the last line is done `etop2p-dev-state.txt` says what the screen shows.
 * Positions are in GUI pixels (the scaled ones a screen lays out in), from the top-left.
 *
 *     move <x> <y>
 *     down <button> | up <button> | click <button>       0 left, 1 right
 *     wheel <notches>                                    positive scrolls up
 *     key <lwjgl key code> [ctrl] [shift]                pressed and released
 *     keydown <lwjgl key code> | keyup <lwjgl key code>  held across the lines between, as for a Ctrl-click
 *     type <text>
 *     wait <ticks>
 *     release                                            lets go of every key binding
 *     lang <code>                                        the game's language, for this run only (en_US, zh_CN)
 *
 * A window without the system's focus never has the game's either, so a screen opening does not let go of the use
 * key as it would: `release` after a use in the world, or the use repeats when the screen closes.
 *
 * It reaches into lwjgl3ify's LWJGL 2 input classes by name, which is why it is no part of the mod proper.
 */
object DevInput {

    private val gameDir: File get() = Minecraft.getMinecraft().mcDataDir
    private val pending = ArrayDeque<String>()
    private var waiting = 0

    private val mouse: Class<*> by lazy { Class.forName("org.lwjglx.input.Mouse") }
    private val keyboard: Class<*> by lazy { Class.forName("org.lwjglx.input.Keyboard") }

    fun register() {
        FMLCommonHandler.instance().bus().register(this)
        EtoP2P.LOG.info("Dev input on: write {} to drive the screen", File(gameDir, INPUT))
    }

    @SubscribeEvent
    fun onTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.START) return
        if (pending.isEmpty()) {
            val file = File(gameDir, INPUT)
            if (!file.isFile) return
            pending += file.readLines().map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") }
            file.delete()
            // The state is written a few ticks after the last line, when the screen has taken it in; its file
            // appearing is how a test knows the lines are done.
            File(gameDir, STATE).delete()
            pending += listOf("wait 3", "state")
        }
        if (waiting > 0) {
            waiting--
            return
        }
        val line = pending.removeFirst()
        runCatching { run(line) }.onFailure { EtoP2P.LOG.error("dev input '{}' failed", line, it) }
    }

    private fun run(line: String) {
        val words = line.split(' ')
        when (words[0]) {
            "move" -> moveTo(words[1].toInt(), words[2].toInt())
            "down" -> button(words[1].toInt(), true)
            "up" -> button(words[1].toInt(), false)
            "click" -> {
                button(words[1].toInt(), true)
                pending.addFirst("up ${words[1]}")
            }
            "wheel" -> mouse.getMethod("addWheelEvent", Double::class.javaPrimitiveType).invoke(null, words[1].toDouble())
            "key" -> {
                val mods = words.drop(2).mapNotNull { MODIFIERS[it] }
                if (mods.isEmpty()) {
                    key(words[1].toInt(), true)
                    key(words[1].toInt(), false)
                } else {
                    // A modifier has to be seen held when the key is read, so it goes down a tick before and up a tick after.
                    val steps = mods.map { "keydown $it" } + "wait 1" + "key ${words[1]}" + "wait 1" + mods.map { "keyup $it" }
                    steps.asReversed().forEach(pending::addFirst)
                }
            }
            "keydown" -> key(words[1].toInt(), true)
            "keyup" -> key(words[1].toInt(), false)
            // Typed text is an event of its own, apart from the keys.
            "type" -> {
                val event = Class.forName("me.eigenraven.lwjgl3ify.api.InputEvents\$TextEvent")
                Class.forName("me.eigenraven.lwjgl3ify.api.InputEvents").getMethod("injectTextEvent", event)
                    .invoke(null, event.getConstructor(String::class.java).newInstance(line.removePrefix("type ")))
            }
            "wait" -> waiting = words[1].toInt()
            "release" -> KeyBinding.unPressAllKeys()
            // Only the texts are read again, not every resource; a screen already open keeps the widths it measured.
            "lang" -> Minecraft.getMinecraft().let { mc ->
                val manager = mc.languageManager
                manager.currentLanguage = manager.languages.first { it.languageCode == words[1] }
                manager.onResourceManagerReload(mc.resourceManager)
            }
            "state" -> File(gameDir, STATE).writeText(state())
            else -> error("unknown dev input")
        }
    }

    /** A move is the position changing: the one a poll answers with, and the latest one, which stamps each event. */
    private fun moveTo(guiX: Int, guiY: Int) {
        val mc = Minecraft.getMinecraft()
        val scale = ScaledResolution(mc, mc.displayWidth, mc.displayHeight).scaleFactor
        val x = guiX * scale + scale / 2
        // LWJGL counts y from the bottom of the window.
        val y = mc.displayHeight - 1 - (guiY * scale + scale / 2)
        for ((field, value) in listOf("latestX" to x, "x" to x, "latestY" to y, "y" to y)) {
            mouse.getDeclaredField(field).apply { isAccessible = true }.setInt(null, value)
        }
    }

    /** Besides the event, the held-buttons word a poll answers from, as SDL numbers them: left 1, middle 2, right 3. */
    private fun button(button: Int, pressed: Boolean) {
        mouse.getMethod("addButtonEvent", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType).invoke(null, button, pressed)
        val bit = 1 shl (intArrayOf(1, 3, 2).getOrElse(button) { button + 1 } - 1)
        val held = mouse.getField("sdlMouseButtonFlags")
        held.setInt(null, if (pressed) held.getInt(null) or bit else held.getInt(null) and bit.inv())
    }

    /** Besides the event, SDL's table of held keys that `isKeyDown` reads, or a held Ctrl would go unseen. */
    private fun key(code: Int, pressed: Boolean) {
        val eventClass = Class.forName("org.lwjglx.input.Keyboard\$KeyEvent")
        val stateClass = Class.forName("org.lwjglx.input.Keyboard\$KeyState")
        val state = stateClass.enumConstants.first { (it as Enum<*>).name == if (pressed) "PRESS" else "RELEASE" }
        val event = eventClass
            .getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, stateClass, Long::class.javaPrimitiveType)
            .newInstance(code, code, 0, state, System.nanoTime())
        keyboard.getMethod("addRawKeyEvent", eventClass).invoke(null, event)
        val scancode = Class.forName("org.lwjglx.input.KeyCodes")
            .getMethod("lwjglToSdlScancode", Int::class.javaPrimitiveType).invoke(null, code) as Int
        val held = keyboard.getField("sdlKeyPressedArray").get(null) as? ByteBuffer ?: return
        if (scancode in 1 until held.limit()) held.put(scancode, if (pressed) 1 else 0)
    }

    private fun state(): String {
        val mc = Minecraft.getMinecraft()
        val resolution = ScaledResolution(mc, mc.displayWidth, mc.displayHeight)
        val screen = ScreenState.current
        return buildString {
            appendLine("display ${mc.displayWidth} ${mc.displayHeight} scale ${resolution.scaleFactor} gui ${resolution.scaledWidth} ${resolution.scaledHeight}")
            appendLine("screen ${mc.currentScreen?.javaClass?.name}")
            appendLine("tunnels ${ClientState.entries.size} undo ${ClientState.undo} notice ${ClientState.notice?.key}")
            if (screen != null) {
                appendLine("view ${screen.view} sort ${screen.sort} filter ${screen.filter}")
                appendLine("shown ${screen.shown.size} groups ${screen.groups.size} collapsed ${screen.collapsed}")
                appendLine("primary ${screen.primary?.let { "${it.x},${it.y},${it.z},${it.side}" }}")
                appendLine("selected " + screen.selected.joinToString(" ") { "${it.x},${it.y},${it.z},${it.side}" })
            }
        }
    }

    private const val INPUT = "etop2p-dev-input.txt"
    private const val STATE = "etop2p-dev-state.txt"
    private val MODIFIERS = mapOf("ctrl" to 29, "shift" to 42)
}
