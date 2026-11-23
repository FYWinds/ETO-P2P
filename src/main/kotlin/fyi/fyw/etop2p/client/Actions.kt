package fyi.fyw.etop2p.client

import fyi.fyw.etolib.ui.Dialogs
import fyi.fyw.etolib.ui.Icons
import fyi.fyw.etolib.ui.Menu
import fyi.fyw.etolib.ui.UiText.tr
import fyi.fyw.etop2p.client.ui.Palette
import fyi.fyw.etop2p.data.Op
import fyi.fyw.etop2p.data.P2PEntry
import fyi.fyw.etop2p.data.P2PTypes
import net.minecraft.client.gui.GuiScreen
import net.minecraftforge.common.util.ForgeDirection

/** What can be done with the selection: the menu, the keys and the details' buttons all end here. */
class Actions(private val state: ScreenState) {

    private val picked: List<P2PEntry> get() = state.selection
    private val primary: P2PEntry? get() = state.entry(state.primary) ?: picked.lastOrNull()

    /** One tunnel of each connection the selection touches (an unbound tunnel stands for itself). */
    private val connections: List<P2PEntry>
        get() = picked.distinctBy { if (it.frequency == 0L) it.key else it.frequency }

    private val frequencies: List<Long> get() = picked.map { it.frequency }.filter { it != 0L }.distinct()

    /** What a prompt asks for; with several to name it says so, and that `{n}` numbers them. */
    private fun asking(key: String, count: Int): String = tr(key).let { if (count > 1) tr("etop2p.ask.numbered", it, count) else it }

    /** AE2's name, which a connection shares: one pattern names every connection picked. */
    fun rename() {
        val targets = connections.ifEmpty { return }
        Dialogs.prompt(state.panel, asking("etop2p.ask.name", targets.size), primary?.name ?: "") { text ->
            val names = Listing.numbered(text.trim(), targets.size)
            ClientState.send(Op.Rename(targets.mapIndexed { i, entry -> entry.key to names[i] }))
        }
    }

    /** The card's own text for single tunnels. */
    fun label() {
        val targets = picked.ifEmpty { return }
        Dialogs.prompt(state.panel, asking("etop2p.ask.label", targets.size), primary?.label ?: "") { text ->
            val labels = Listing.numbered(text.trim(), targets.size)
            ClientState.send(Op.Label(targets.mapIndexed { i, entry -> entry.key to labels[i] }))
        }
    }

    fun labelFromAttached() {
        val labels = picked.map { it.key to GameTexts.attachedName(it) }.filter { it.second.isNotEmpty() }
        if (labels.isNotEmpty()) ClientState.send(Op.Label(labels))
    }

    fun makeInput() {
        primary?.takeIf { it.output && it.frequency != 0L }?.let { ClientState.send(Op.MakeInput(it.key)) }
    }

    /** The picked tunnels become outputs of [input], which becomes an input if it is not one. */
    fun bindTo(input: P2PEntry) {
        val outputs = picked.map { it.key }.filter { it != input.key }
        if (outputs.isNotEmpty()) ClientState.send(Op.Bind(input.key, outputs))
    }

    /**
     * What dropping the selection on [target] means: into its connection when it has one (under that connection's
     * input), else under [target] itself.
     */
    fun dropOn(target: P2PEntry) = bindTo(state.inputOf(target.frequency) ?: target)

    fun unbind() {
        if (picked.isNotEmpty()) ClientState.send(Op.Unbind(picked.map { it.key }))
    }

    fun convert(type: Int) {
        if (picked.isNotEmpty()) ClientState.send(Op.Convert(picked.map { it.key }, type))
    }

    fun favorite(on: Boolean) = meta(favorite = on)

    fun color(tag: Int) = meta(color = tag)

    fun note() {
        val current = primary?.let { state.meta(it.frequency).note } ?: ""
        if (frequencies.isEmpty()) return
        Dialogs.prompt(state.panel, tr("etop2p.ask.note"), current) { meta(note = it.trim()) }
    }

    private fun meta(color: Int? = null, favorite: Boolean? = null, note: String? = null) {
        if (frequencies.isNotEmpty()) ClientState.send(Op.Meta(frequencies, color, favorite, note))
    }

    fun undo() {
        if (ClientState.undo > 0) ClientState.send(Op.Undo)
    }

    fun copyPlace() {
        primary?.let { GuiScreen.setClipboardString("${it.key.x} ${it.key.y} ${it.key.z}") }
    }

    /** Points the picked tunnels and their connections out in the world and leaves the screen, to go and look. */
    fun locate() {
        val target = primary ?: return
        val shown = picked.flatMap(state::connection).distinctBy { it.key }
        val marks = shown.map {
            Highlights.Mark(it.key, Palette.of(it.frequency, state.meta(it.frequency)), Listing.title(it, GameTexts))
        }
        val lines = shown.filter { it.output }.mapNotNull { out -> state.inputOf(out.frequency)?.let { it.key to out.key } }
        Highlights.show(marks, lines, marks.first { it.key == target.key })
        state.panel.closeIfOpen()
    }

    /** The menu for the selection, at the screen point [x], [y]. */
    fun openMenu(x: Int, y: Int) {
        val target = primary ?: return
        val many = picked.size > 1
        val bound = frequencies.isNotEmpty()
        val favorite = bound && frequencies.all { state.meta(it).favorite }
        val others = ClientState.entries.values
            .filter { !it.output && it.frequency != 0L && it.frequency !in frequencies }
            .sortedBy { Listing.title(it, GameTexts).lowercase() }
        Menu.open(state.panel, x, y) {
            entry(tr("etop2p.menu.rename"), hint = "F2", icon = Icons.PENCIL, action = ::rename)
            entry(tr("etop2p.menu.label"), action = ::label)
            entry(tr("etop2p.menu.label_attached"), enabled = picked.any { it.attachedItem >= 0 }, action = ::labelFromAttached)
            separator()
            entry(tr("etop2p.menu.make_input"), enabled = !many && target.output && target.frequency != 0L, action = ::makeInput)
            if (many) entry(tr("etop2p.menu.bind_rest", picked.size - 1)) { bindTo(target) }
            submenu(tr("etop2p.menu.bind_into"), enabled = others.isNotEmpty()) {
                others.take(MENU_CONNECTIONS).forEach { input ->
                    entry(Listing.title(input, GameTexts), hint = Listing.frequencyCode(input.frequency), item = GameTexts.typeStack(input.type)) {
                        bindTo(input)
                    }
                }
            }
            entry(tr("etop2p.menu.unbind"), hint = "Del", enabled = bound, action = ::unbind)
            submenu(tr("etop2p.menu.convert")) {
                P2PTypes.available.forEach { type ->
                    entry(GameTexts.typeName(type.index), item = type.stack, checked = !many && target.type == type.index) { convert(type.index) }
                }
            }
            separator()
            entry(tr(if (favorite) "etop2p.menu.unfavorite" else "etop2p.menu.favorite"), enabled = bound) { favorite(!favorite) }
            submenu(tr("etop2p.menu.color"), enabled = bound) {
                entry(tr("etop2p.menu.color_none")) { color(0) }
                for (tag in 1 until Palette.TAGS.size) {
                    entry(tr("etop2p.color.$tag"), icon = Palette.swatch(Palette.TAGS[tag])) { color(tag) }
                }
            }
            entry(tr("etop2p.menu.note"), icon = Icons.COMMENT, enabled = bound, action = ::note)
            separator()
            entry(tr("etop2p.menu.locate"), icon = Icons.PIN, action = ::locate)
            entry(tr("etop2p.menu.copy_place"), hint = "${target.key.x} ${target.key.y} ${target.key.z}", action = ::copyPlace)
        }
    }

    companion object {
        /** How many connections the "bind into" submenu lists; the rest are reached by dragging. */
        private const val MENU_CONNECTIONS = 14

        fun sideName(side: Int): String = tr("etop2p.side." + ForgeDirection.getOrientation(side).name.lowercase())
    }
}
