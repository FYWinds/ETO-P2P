package fyi.fyw.etop2p.client.ui

import fyi.fyw.etolib.ui.Icon
import fyi.fyw.etolib.ui.Icons
import fyi.fyw.etolib.ui.Look
import fyi.fyw.etop2p.data.LinkMeta
import java.awt.Color

/** The colours connections are told apart by. */
internal object Palette {

    /** The colour tags a player can give a connection; index 0 is "none". */
    val TAGS = intArrayOf(
        0, 0xFFE05252.toInt(), 0xFFF59A5C.toInt(), 0xFFFFD257.toInt(), 0xFF5EE9B5.toInt(),
        0xFF22D3EE.toInt(), 0xFF78B6F4.toInt(), 0xFFB48CF2.toInt(), 0xFFF28CC8.toInt(),
    )

    /** A connection's colour: its tag when it has one, else a hue that follows from its frequency. */
    fun of(frequency: Long, meta: LinkMeta?): Int = when {
        frequency == 0L -> Look.OFF_INK
        meta != null && meta.color in 1 until TAGS.size -> TAGS[meta.color]
        else -> {
            val mixed = (frequency xor (frequency ushr 29)) * -0x61c8864680b583ebL
            val hue = (mixed ushr 40).toFloat() / (1 shl 24)
            Color.HSBtoRGB(hue, 0.55f, 0.95f) or (0xFF shl 24)
        }
    }

    /** A square of one colour where an icon goes: a colour tag in a menu. */
    fun swatch(argb: Int) = Icon { x, y, _ -> Look.roundRect(x + 1, y + 1, Icons.SIZE - 2, Icons.SIZE - 2, 2, argb) }
}
