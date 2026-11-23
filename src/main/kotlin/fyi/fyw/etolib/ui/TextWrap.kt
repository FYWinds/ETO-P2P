package fyi.fyw.etolib.ui

private const val NO_BREAK_BEFORE = "，。、；：！？）】》〉」』”’…—·,.;:!?)]}%"
private const val NO_BREAK_AFTER = "（【《〈「『“‘([{"

/**
 * Text broken into lines no wider than a given width, for any language. A line may end at a space (which is dropped)
 * and between two characters when one of them is Chinese, Japanese or Korean, where words are not set apart by spaces;
 * never before closing punctuation or after opening punctuation, and never inside a formatting code. A newline in the
 * text always ends a line. A stretch with no place to break that is wider than the width is cut where it stops
 * fitting.
 */
object TextWrap {

    /** [text] in lines at most [maxWidth] wide as [width] measures them (the game font by default); at least one line. */
    fun wrap(text: String, maxWidth: Int, width: (String) -> Int = { it.guiWidth }): List<String> {
        val lines = ArrayList<String>()
        for (paragraph in text.split("\n")) wrapParagraph(paragraph, maxWidth, width, lines)
        return lines
    }

    private fun wrapParagraph(text: String, maxWidth: Int, width: (String) -> Int, lines: MutableList<String>) {
        val first = lines.size
        var start = skipSpaces(text, 0)
        while (start < text.length) {
            if (width(text.substring(start)) <= maxWidth) {
                lines += text.substring(start)
                return
            }
            // The last place to break that still fits; failing that, the last character that fits.
            var best = -1
            var cut = if (insideCode(text, start + 1) && start + 2 <= text.length) start + 2 else start + 1
            for (i in start + 1 until text.length) {
                if (width(text.substring(start, i)) > maxWidth) break
                if (!insideCode(text, i)) cut = i
                if (text[i] == ' ' || breakable(text, i)) best = i
            }
            val end = if (best > start) best else cut
            lines += text.substring(start, end)
            start = skipSpaces(text, end)
        }
        if (lines.size == first) lines += ""
    }

    private fun skipSpaces(text: String, from: Int): Int {
        var i = from
        while (i < text.length && text[i] == ' ') i++
        return i
    }

    /** Whether a line may end before the character at [i] with no space there. */
    private fun breakable(text: String, i: Int): Boolean {
        val before = text[i - 1]
        val after = text[i]
        return when {
            insideCode(text, i) -> false
            !before.isWide && !after.isWide -> false
            else -> after !in NO_BREAK_BEFORE && before !in NO_BREAK_AFTER
        }
    }

    /** Whether [i] falls between a formatting code's section sign and its letter. */
    private fun insideCode(text: String, i: Int): Boolean = i > 0 && text[i - 1] == '\u00a7'

    /** A character of a script written without spaces between words, or its full-width punctuation. */
    private val Char.isWide: Boolean
        get() = code in 0x2E80..0x9FFF ||
            code in 0xAC00..0xD7AF ||
            code in 0xF900..0xFAFF ||
            code in 0xFF00..0xFFEF ||
            code in 0x3000..0x303F
}
