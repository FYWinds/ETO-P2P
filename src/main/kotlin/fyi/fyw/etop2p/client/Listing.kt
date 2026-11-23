package fyi.fyw.etop2p.client

import fyi.fyw.etop2p.data.LinkMeta
import fyi.fyw.etop2p.data.P2PEntry

/** How the screen shows the tunnels. */
enum class View { GROUPED, CARDS, LIST }

enum class Sort { NAME, DISTANCE, TYPE, FREQUENCY, CHANNELS }

/** A yes/no property of a tunnel that the filter can ask for either way. */
enum class Tri {
    ANY, YES, NO;

    fun allows(value: Boolean) = this == ANY || (this == YES) == value

    fun next() = entries[(ordinal + 1) % entries.size]
}

/**
 * What the list is narrowed to. [text] is what was typed: words every one of which must be found in one of the
 * tunnel's texts, and BetterP2P's tags `@in`, `@out`, `@b`, `@u` and `@type=a;b`, which narrow like the buttons do.
 */
data class Filter(
    val text: String = "",
    val types: Set<Int> = emptySet(),
    val output: Tri = Tri.ANY,
    val bound: Tri = Tri.ANY,
    val online: Tri = Tri.ANY,
    val favorites: Boolean = false,
)

/** A connection: the tunnels of one frequency. [frequency] 0 holds the unbound tunnels of one type. */
class Group(
    val frequency: Long,
    val type: Int,
    val input: P2PEntry?,
    val outputs: List<P2PEntry>,
    val meta: LinkMeta,
) {
    val members: List<P2PEntry> get() = listOfNotNull(input) + outputs
    val bound: Boolean get() = frequency != 0L

    /** AE2's name of the connection: its tunnels share it. */
    val name: String get() = input?.name?.takeIf { it.isNotEmpty() } ?: outputs.firstOrNull { it.name.isNotEmpty() }?.name ?: ""
}

/** The texts of a tunnel the screen and the search use besides its own fields, looked up in the game. */
interface Texts {
    fun typeName(type: Int): String

    fun attachedName(entry: P2PEntry): String

    /** Whether [text] holds what was [typed], both lower case; the place for pinyin. */
    fun matches(text: String, typed: String): Boolean

    fun distance(entry: P2PEntry): Double
}

/** Narrowing, ordering and grouping the tunnels: everything about the list that needs no screen. */
object Listing {

    /** The title a tunnel goes by: its label, else what it faces, else the connection's name, else its type. */
    fun title(entry: P2PEntry, texts: Texts): String =
        entry.label.ifEmpty { texts.attachedName(entry).ifEmpty { entry.name.ifEmpty { texts.typeName(entry.type) } } }

    fun frequencyCode(frequency: Long): String = String.format("%04X", frequency and 0xFFFF)

    fun filter(entries: Collection<P2PEntry>, links: Map<Long, LinkMeta>, filter: Filter, texts: Texts): List<P2PEntry> {
        var output = filter.output
        var bound = filter.bound
        val typeWords = ArrayList<String>()
        val words = ArrayList<String>()
        for (word in filter.text.lowercase().split(' ').filter { it.isNotEmpty() }) {
            when {
                word == "@in" -> output = Tri.NO
                word == "@out" -> output = Tri.YES
                word == "@b" -> bound = Tri.YES
                word == "@u" -> bound = Tri.NO
                word.startsWith("@type=") || word.startsWith("@types=") ->
                    typeWords += word.substringAfter('=').split(';').filter { it.isNotEmpty() }
                else -> words += word
            }
        }
        return entries.filter { entry ->
            if (filter.types.isNotEmpty() && entry.type !in filter.types) return@filter false
            if (!output.allows(entry.output) || !bound.allows(entry.frequency != 0L)) return@filter false
            if (!filter.online.allows(entry.online)) return@filter false
            val meta = links[entry.frequency]
            if (filter.favorites && meta?.favorite != true) return@filter false
            val typeName = texts.typeName(entry.type).lowercase()
            if (typeWords.isNotEmpty() && typeWords.none { texts.matches(typeName, it) }) return@filter false
            if (words.isEmpty()) return@filter true
            val haystack = listOf(
                entry.name, entry.label, texts.attachedName(entry), typeName, meta?.note ?: "",
                "${entry.key.x},${entry.key.y},${entry.key.z}", "${entry.key.x} ${entry.key.y} ${entry.key.z}",
                frequencyCode(entry.frequency),
            ).map(String::lowercase)
            words.all { word -> haystack.any { texts.matches(it, word) } }
        }
    }

    fun comparator(sort: Sort, texts: Texts): Comparator<P2PEntry> {
        val byPlace = compareBy<P2PEntry>({ it.key.dim }, { it.key.x }, { it.key.y }, { it.key.z }, { it.key.side })
        val first: Comparator<P2PEntry> = when (sort) {
            Sort.NAME -> compareBy { title(it, texts).lowercase() }
            Sort.DISTANCE -> compareBy { texts.distance(it) }
            Sort.TYPE -> compareBy { it.type }
            Sort.FREQUENCY -> compareBy { it.frequency }
            Sort.CHANNELS -> compareByDescending { it.channels }
        }
        return first.then(byPlace)
    }

    /**
     * The shown tunnels as connections: favourites first, then the bound ones in the order of their first tunnel
     * (so the sort carries over), then the unbound ones by type. A connection is listed whole when any tunnel of it
     * is shown, so narrowing to one output still shows what it belongs to; [all] is every tunnel of the network.
     */
    fun groups(shown: List<P2PEntry>, all: Collection<P2PEntry>, links: Map<Long, LinkMeta>, whole: Boolean): List<Group> {
        val byFrequency = (if (whole) all else shown).filter { it.frequency != 0L }.groupBy { it.frequency }
        val bound = shown.filter { it.frequency != 0L }.map { it.frequency }.distinct().map { frequency ->
            val members = byFrequency.getValue(frequency)
            val input = members.firstOrNull { !it.output }
            Group(frequency, (input ?: members.first()).type, input, members.filter { it.output }, links[frequency] ?: LinkMeta())
        }
        val unbound = shown.filter { it.frequency == 0L }.groupBy { it.type }.map { (type, members) ->
            Group(0L, type, null, members, LinkMeta())
        }
        return bound.sortedByDescending { it.meta.favorite } + unbound
    }

    /**
     * The texts for renaming several things with one pattern: `{n}` in [pattern] becomes a running number from
     * [start]; a pattern without it gets the number appended after a space, unless there is only one thing to name.
     */
    fun numbered(pattern: String, count: Int, start: Int = 1): List<String> {
        if (count == 1 && "{n}" !in pattern) return listOf(pattern)
        val template = if ("{n}" in pattern) pattern else "$pattern {n}"
        return List(count) { template.replace("{n}", (start + it).toString()) }
    }
}
