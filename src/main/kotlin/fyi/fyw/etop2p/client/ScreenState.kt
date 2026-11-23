package fyi.fyw.etop2p.client

import com.cleanroommc.modularui.screen.ModularPanel
import fyi.fyw.etop2p.data.LinkMeta
import fyi.fyw.etop2p.data.P2PEntry
import fyi.fyw.etop2p.data.P2PKey

/**
 * What one open screen shows and has picked: the narrowing, the order, the view, the selection. The lists are
 * worked out again only when the server sent something or the player changed how to look at it.
 */
class ScreenState {

    lateinit var panel: ModularPanel

    var filter = Filter()
    var view: View by Prefs::view
    var sort: Sort by Prefs::sort

    /** The connections folded away in the grouped view, by frequency; 0 is the unbound tunnels. */
    val collapsed = HashSet<Long>()

    val selected = LinkedHashSet<P2PKey>()

    /** The tunnel last clicked: the one the details show and a menu is about. */
    var primary: P2PKey? = null

    /** Where a shift-click's run starts. */
    var anchor: P2PKey? = null

    private var builtFor: Any? = null

    /** The tunnels the narrowing lets through, in order. */
    var shown: List<P2PEntry> = emptyList()
        private set

    /** [shown] as connections, each whole. */
    var groups: List<Group> = emptyList()
        private set

    /** Changes whenever [shown] or [groups] were worked out again. */
    var built = 0
        private set

    /** Brings the lists up to date; cheap when nothing changed. */
    fun refresh() {
        val wanted = listOf(ClientState.revision, filter, sort, view)
        if (wanted == builtFor) return
        builtFor = wanted
        val entries = ClientState.entries.values
        shown = Listing.filter(entries, ClientState.links, filter, GameTexts).sortedWith(Listing.comparator(sort, GameTexts))
        groups = Listing.groups(shown, entries, ClientState.links, whole = true)
        // What is out of sight is not picked: nothing is done to a tunnel the player cannot see.
        val inSight = if (view == View.GROUPED) groups.flatMap { it.members } else shown
        selected.retainAll(inSight.mapTo(HashSet()) { it.key })
        if (primary !in selected) primary = selected.lastOrNull()
        built++
    }

    fun entry(key: P2PKey?): P2PEntry? = key?.let(ClientState.entries::get)

    val selection: List<P2PEntry> get() = selected.mapNotNull(ClientState.entries::get)

    /** Every tunnel of the connection [entry] belongs to; itself alone when it is unbound. */
    fun connection(entry: P2PEntry): List<P2PEntry> =
        if (entry.frequency == 0L) listOf(entry) else ClientState.entries.values.filter { it.frequency == entry.frequency }

    fun inputOf(frequency: Long): P2PEntry? =
        if (frequency == 0L) null else ClientState.entries.values.firstOrNull { it.frequency == frequency && !it.output }

    fun meta(frequency: Long): LinkMeta = ClientState.links[frequency] ?: NO_META

    fun select(keys: Collection<P2PKey>, add: Boolean = false) {
        if (!add) selected.clear()
        selected += keys
        primary = keys.lastOrNull() ?: primary
        anchor = primary
    }

    companion object {
        private val NO_META = LinkMeta()

        /** The state of the screen opened last, for the dev tools to read. */
        var current: ScreenState? = null
    }
}
