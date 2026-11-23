package fyi.fyw.etolib.ui

import net.minecraft.util.ResourceLocation

/** A small picture drawn in one colour, [Icons.SIZE] by [Icons.SIZE] GUI pixels, its top-left at the point given. */
fun interface Icon {

    fun draw(x: Int, y: Int, color: Int)

    /**
     * Draws it in the middle of a key [w] by [h] at [x], [y]: the middle of its lit face (the dark edge right and below
     * melts into the bar), its rows level with a label's letters. Exact on a key of even width and height.
     */
    fun drawCentred(x: Int, y: Int, w: Int, h: Int, color: Int) =
        draw(x + (w - 1 - Icons.SIZE) / 2, y + (h - 1 - Icons.SIZE) / 2, color)
}

/**
 * The kit's small pictures, 9 by 9 in GUI pixels and one colour each (strokes two pixels thick, as the game's own icons
 * are, so they hold up beside its font). Drawn from rows of text: '#' in the colour, '.' left clear.
 */
object Icons {

    const val SIZE = 9

    /** A picture from rows of text, '#' set and '.' clear, each row [SIZE] long. */
    fun pixels(vararg rows: String): Icon = Pixels(rows, false)

    /** A bundled texture drawn into the icon's square in the colour asked for (its shape alone, as [Look.texture]). */
    fun texture(location: ResourceLocation): Icon = Icon { x, y, color ->
        Look.texture(location, x.toFloat(), y.toFloat(), SIZE.toFloat(), SIZE.toFloat(), color)
    }

    private val UNDO_ROWS = arrayOf(
        "..#......", ".##......", "#######..", "########.", ".##...##.", "..#...##.",
        "......##.", "..#####..", "..####...",
    )

    /** A hooked arrow, its head pointing left and its tail curling under. */
    val UNDO: Icon = Pixels(UNDO_ROWS, false)

    /** The undo arrow turned the other way. */
    val REDO: Icon = Pixels(UNDO_ROWS, true)

    /** A beetle: antennae, a head, a back split down the middle, three legs a side. */
    val BUG = pixels(
        ".#.....#.", "..#...#..", "...###...", "#.#####.#", ".###.###.", "#.##.##.#",
        ".###.###.", "#.##.##.#", "..##.##..",
    )

    /** A cog of eight teeth round a hub. */
    val GEAR = pixels(
        "..##.##..", "..#####..", "#########", "###...###", ".##...##.", "###...###",
        "#########", "..#####..", "..##.##..",
    )

    /** A question mark. */
    val HELP = pixels(
        "..#####..", ".##...##.", "......##.", ".....##..", "....##...", "....##...",
        ".........", "....##...", "....##...",
    )

    /** A light bulb on its screw. */
    val BULB = pixels(
        "...###...", "..##.##..", ".##...##.", ".##...##.", "..##.##..", "...###...",
        ".........", "...###...", "...###...",
    )

    /** A tick. */
    val CHECK = pixels(
        ".........", ".......##", "......###", ".....###.", "##..###..", "######...",
        ".####....", "..##.....", ".........",
    )

    /** A speech bubble, for comments. */
    val COMMENT = pixels(
        ".#######.", "#########", "##.....##", "##.....##", "#########", ".#######.",
        "..##.....", ".##......", ".........",
    )

    /** A card with its header. */
    val CARD = pixels(
        "#########", "#########", "#.......#", "#########", "#.......#", "#.......#",
        "#.......#", "#########", ".........",
    )

    /** A map pin, for a spot in a world. */
    val PIN = pixels(
        "..#####..", ".#######.", ".##...##.", ".##...##.", ".#######.", "..#####..",
        "...###...", "...###...", "....#....",
    )

    /** A camera, for a screenshot. */
    val CAMERA = pixels(
        ".........", "...###...", "#########", "###...###", "##.....##", "###...###",
        "#########", ".........", ".........",
    )

    /** An open book. */
    val BOOK = pixels(
        ".........", "##.....##", "####.####", "####.####", "####.####", "####.####",
        "####.####", ".##...##.", ".........",
    )

    /** A plus, for adding. */
    val PLUS = pixels(
        ".........", "...##....", "...##....", ".######..", ".######..", "...##....",
        "...##....", ".........", ".........",
    )

    /** A pencil, its point at the bottom left: writing, renaming. */
    val PENCIL = pixels(
        "......##.", ".....###.", "....###..", "...###...", "..###....", ".###.....",
        "####.....", "###......", ".........",
    )

    /** Four squares: everything, laid out as a grid. */
    val GRID = pixels(
        "####.####", "####.####", "####.####", "####.####", ".........", "####.####",
        "####.####", "####.####", "####.####",
    )

    /** An arrow up out of a tray: sharing. */
    val SHARE = pixels(
        "...##....", "..####...", ".######..", "...##....", "##.##.##.", "##....##.",
        "##....##.", "########.", "########.",
    )

    /** A person: an account, a player. */
    val PERSON = pixels(
        "...###...", "..#####..", "..#####..", "...###...", ".........", ".#######.",
        "#########", "#########", "#########",
    )

    /** A magnifying glass, for searching. */
    val SEARCH = pixels(
        ".####....", "##..##...", "#....#...", "#....#...", "##..##...", ".#####...",
        ".....##..", "......##.", ".......##",
    )

    /** A cross, for clearing or closing. */
    val CROSS = pixels(
        ".........", ".##...##.", ".###.###.", "..#####..", "...###...", "..#####..",
        ".###.###.", ".##...##.", ".........",
    )

    /** A chevron pointing right: more lies that way (a submenu). */
    val ARROW = pixels(
        ".........", "..##.....", "...##....", "....##...", ".....##..", "....##...",
        "...##....", "..##.....", ".........",
    )

    /** A picture from rows of text, as runs of set pixels worked out once: one rectangle a run. */
    private class Pixels(rows: Array<out String>, mirror: Boolean) : Icon {

        /** Each run as row, left, length. */
        private val runs: IntArray

        init {
            val found = ArrayList<Int>()
            for ((row, r) in rows.withIndex()) {
                var col = 0
                while (col < r.length) {
                    if (r[col] != '#') {
                        col++
                        continue
                    }
                    val from = col
                    while (col < r.length && r[col] == '#') col++
                    found += row
                    found += if (mirror) r.length - col else from
                    found += col - from
                }
            }
            runs = found.toIntArray()
        }

        override fun draw(x: Int, y: Int, color: Int) = Look.batch {
            for (i in runs.indices step 3) Look.rect(x + runs[i + 1], y + runs[i], runs[i + 2], 1, color)
        }
    }
}
