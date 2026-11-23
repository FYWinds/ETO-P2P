package fyi.fyw.etop2p.data

/** One tunnel part: the dimension and block of its cable bus and the side (a ForgeDirection ordinal) it sits on. */
data class P2PKey(val dim: Int, val x: Int, val y: Int, val z: Int, val side: Int)

/**
 * A tunnel as the screen shows it. [name] is AE2's custom name, which a bound tunnel shares with its whole
 * connection; [label] is this card's own text for the one tunnel. [frequency] 0 is an unbound tunnel.
 * [channels] is what an ME tunnel carries, -1 for the other types. [attachedItem] and [attachedMeta] are the item
 * of the block or part the tunnel faces, -1 for nothing.
 */
data class P2PEntry(
    val key: P2PKey,
    val type: Int,
    val name: String,
    val label: String,
    val frequency: Long,
    val output: Boolean,
    val online: Boolean,
    val channels: Int,
    val attachedItem: Int,
    val attachedMeta: Int,
)

/** What the card keeps for a connection (a frequency): a colour tag (0 none), the favourite mark and a note. */
data class LinkMeta(val color: Int = 0, val favorite: Boolean = false, val note: String = "") {
    val isEmpty: Boolean get() = color == 0 && !favorite && note.isEmpty()
}

/** A line for the player after an operation: a lang key with its arguments. */
data class Notice(val key: String, val args: List<String>, val error: Boolean)

/** What the player asks the server to do with the tunnels of the network the screen is open on. */
sealed class Op {
    /** [input] becomes (or stays) an input and every tunnel of [outputs] an output of it, converted to its type. */
    data class Bind(val input: P2PKey, val outputs: List<P2PKey>) : Op()

    /** [key], an output, swaps places with the input of its connection. */
    data class MakeInput(val key: P2PKey) : Op()

    data class Unbind(val keys: List<P2PKey>) : Op()

    /** AE2's name: of the whole connection for a bound tunnel. */
    data class Rename(val names: List<Pair<P2PKey, String>>) : Op()

    data class Label(val labels: List<Pair<P2PKey, String>>) : Op()

    /** The tunnels and, for a bound one, its whole connection become tunnels of [type]. */
    data class Convert(val keys: List<P2PKey>, val type: Int) : Op()

    /** A null field stays as it is. */
    data class Meta(val frequencies: List<Long>, val color: Int?, val favorite: Boolean?, val note: String?) : Op()

    object Undo : Op()

    object Close : Op()
}
