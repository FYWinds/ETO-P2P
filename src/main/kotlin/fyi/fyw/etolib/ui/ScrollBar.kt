package fyi.fyw.etolib.ui

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The thumb's colour while the mouse is off the bar. */
private const val THUMB = 0xFF6A6C74.toInt()

/**
 * A list's scroll bar along its right edge: a thumb to drag, a track to click (the thumb jumps there), drawn wider and
 * brighter while the mouse is on it. The list owns the scroll; this works it out from the thumb.
 */
class ScrollBar {

    private var x = 0
    private var top = 0
    private var height = 0
    private var thumb = 0
    private var track = 0
    private var most = 0
    private var held = false
    private var grab = 0

    /** Whether the thumb is being dragged. */
    val dragging: Boolean
        get() = held

    /**
     * Draws the bar for a list [visible] tall showing [content] of it from [scroll], against the right edge [right];
     * returns the scroll clamped to the content. Nothing is drawn when everything fits.
     */
    fun draw(right: Int, top: Int, visible: Int, content: Int, scroll: Int, hot: Boolean): Int {
        this.x = right - W - 1
        this.top = top
        this.height = visible
        most = max(0, content - visible)
        val clamped = max(0, min(most, scroll))
        if (most <= 0) return clamped
        thumb = max(14, visible * visible / max(1, content))
        track = visible - thumb
        val y = top + track * clamped / most
        val lit = held || hot
        if (lit) Look.rect(x - 1, top, W + 1, visible, 0x40000000)
        Look.rect(x + 1, y, W - 2, thumb, if (lit) Look.INK else THUMB)
        return clamped
    }

    /** Whether there is a bar (the content overflows), and the point is on it. */
    fun contains(px: Int, py: Int): Boolean = most > 0 && px >= x - 2 && px < x + W + 2 && py >= top && py < top + height

    /** A press on the bar at [y]: takes the thumb where it was grabbed, or centres it on the mouse; the scroll. */
    fun press(y: Int, scroll: Int): Int {
        val thumbTop = top + if (most <= 0) 0 else track * scroll / most
        grab = if (y >= thumbTop && y < thumbTop + thumb) y - thumbTop else thumb / 2
        held = true
        return drag(y)
    }

    /** The scroll for the mouse at [y] while dragging. */
    fun drag(y: Int): Int {
        if (track <= 0) return 0
        val at = max(0, min(track, y - grab - top))
        return (at.toFloat() * most / track).roundToInt()
    }

    /** Lets go; true when it was being dragged. */
    fun release(): Boolean {
        val was = held
        held = false
        return was
    }

    companion object {

        /** The bar's width. */
        const val W = 5
    }
}
