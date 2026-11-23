package fyi.fyw.etolib.ui

import com.cleanroommc.modularui.api.widget.Interactable
import com.cleanroommc.modularui.drawable.GuiDraw
import com.cleanroommc.modularui.utils.Platform
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.FontRenderer
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.Tessellator
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fluids.FluidStack
import org.lwjgl.opengl.GL11
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** This text's width in the game font, in GUI pixels. */
val String.guiWidth: Int
    get() = Look.font.getStringWidth(this)

/** This text cut to fit [maxWidth], ending in "..." when it had to. The game font has one size. */
fun String.fitTo(maxWidth: Int): String {
    if (guiWidth <= maxWidth) return this
    val room = maxWidth - "...".guiWidth
    if (room <= 0) return ""
    return Look.font.trimStringToWidth(this, room) + "..."
}

/** This colour mixed into [other], [share] of this one (0 to 1); opaque. */
fun Int.mixedWith(other: Int, share: Float): Int {
    val s = share.coerceIn(0f, 1f)
    val r = ((this shr 16 and 0xFF) * s + (other shr 16 and 0xFF) * (1 - s)).roundToInt()
    val g = ((this shr 8 and 0xFF) * s + (other shr 8 and 0xFF) * (1 - s)).roundToInt()
    val b = ((this and 0xFF) * s + (other and 0xFF) * (1 - s)).roundToInt()
    return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
}

/** Black or white, whichever reads on this background: a label on a coloured face, with no shadow to blur it. */
val Int.contrastInk: Int
    get() {
        val l = (0.2126f * (this shr 16 and 0xFF) + 0.7152f * (this shr 8 and 0xFF) + 0.0722f * (this and 0xFF)) / 255f
        return if (l > 0.5f) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()
    }

/**
 * The kit's look: Factory Flow's card palette and bevels, NEI's slots, the game font. Every coordinate is in GUI
 * pixels of the current (possibly scaled) widget space. Each shape takes floats, for scaled drawing, or whole pixels,
 * the usual case.
 */
object Look {

    /**
     * The type scale: labels and body text at 1x, figures at 1.5x. At GUI scale 2 both land on whole screen pixels, so
     * the game font stays crisp; 2x reads as shouting next to 1x.
     */
    const val FIGURE = 1.5f

    // Factory Flow's card ramp.
    const val INK = 0xFFE8E9EE.toInt()
    const val MUTED = 0xFF9A9CA4.toInt()
    const val FRAME = 0xFF3C3E45.toInt()
    const val RING = 0xFF52545C.toInt()
    const val HIGHLIGHT = 0xFF5A5C65.toInt()
    const val SHADOW = 0xFF1D1F23.toInt()
    const val TILE = 0xFF36383F.toInt()
    const val TILE_EDGE = 0xFF25272C.toInt()
    const val TILE_HI = 0xFF4E5058.toInt()
    const val KEY = 0xFF26282D.toInt()
    const val KEY_HOVER = 0xFF2D2F35.toInt()
    const val KEY_EDGE = 0xFF111317.toInt()
    const val KEY_HI = 0xFF4A4C54.toInt()
    const val KEY_LO = 0xFF17191D.toInt()
    const val NAMEBAR = 0xFF2D2F35.toInt()
    const val WELL = 0xFF4A4C54.toInt()

    /** The ground behind something rendered in game (a block, a structure). */
    const val PICTURE = 0xFF1A1C20.toInt()

    /** A full-screen backdrop, and the dots of a grid on it. */
    const val CANVAS = 0xFF141414.toInt()
    const val CANVAS_DOT = 0xFF26282D.toInt()
    const val GOLD = 0xFFFFD257.toInt()
    const val SELECTION = 0xFF22D3EE.toInt()

    /** What the mouse is on, the same everywhere. Drawn faint: a thin line and a soft halo. */
    const val LIT = 0xFFFFD257.toInt()
    const val MENU = 0xFF3C3E45.toInt()
    const val MENU_HOVER = 0xFF4E5058.toInt()
    const val RED_INK = 0xFFE05252.toInt()
    const val AMBER_INK = 0xFFE0A63A.toInt()

    /** An EU figure: the number amber, its unit a greyed gold, a quieter one a step down. Never taken for a rate. */
    const val ENERGY = 0xFFFCD34D.toInt()
    const val ENERGY_TAIL = 0xFF8C7D4C.toInt()
    const val ENERGY_QUIET = 0xFFC9AE5A.toInt()

    /** What comes in (inputs, sources): GregTech's blue for inputs, soft as the rest of the ramp. */
    const val SOURCE_INK = 0xFF78B6F4.toInt()

    /** What goes out (outputs, products): GregTech's orange for outputs, soft, kept clear of the gold. */
    const val PRODUCT_INK = 0xFFF59A5C.toInt()

    /** All is well: a mint, apart from the inputs' and outputs' colours. */
    const val OK_INK = 0xFF5EE9B5.toInt()

    /** The ink of a control that can do nothing now. */
    const val OFF_INK = 0xFF5A5C65.toInt()

    /** The ink of the hint in an empty text box. */
    const val HINT = 0xFF6A6C74.toInt()

    /** A line between groups of rows. */
    const val RULE = 0xFF3A3C42.toInt()

    /** A text box's face, and the colour behind its selected text. */
    const val FIELD = 0xFF15171C.toInt()
    const val FIELD_SELECTION = 0xFF35507A.toInt()

    /** A chip's face, and the same under the mouse. */
    const val CONTROL = 0xFF2A2C31.toInt()
    const val CONTROL_HOVER = 0xFF33363C.toInt()

    /** The card surface a picked chip's colour is mixed into. */
    const val SURFACE = 0xFF23252A.toInt()

    /** A chip's edge, and the same under the mouse. */
    const val CHIP_EDGE = 0xFF3A3C42.toInt()
    const val CHIP_EDGE_HOVER = 0xFF5A5D66.toInt()

    // NEI slots.
    const val SLOT_ITEM = 0xFF8B8B8B.toInt()
    const val SLOT_ITEM_TL = 0xFF373737.toInt()
    const val SLOT_ITEM_BR = 0xFFFFFFFF.toInt()
    const val SLOT_FLUID = 0xFF2B2B2B.toInt()
    const val SLOT_FLUID_TL = 0xFF151515.toInt()
    const val SLOT_FLUID_BR = 0xFF5A5A5A.toInt()

    /** How far a drawing fades before its items and fluids drop out. */
    private const val FADE_ITEMS = 0.35f

    private val SHADOW_GROW = floatArrayOf(2f, 1f, 0f)
    private val SHADOW_SHADE = intArrayOf(0x15000000, 0x25000000, 0x3E000000)

    val font: FontRenderer
        get() = Minecraft.getMinecraft().fontRenderer

    /** The screen's width in GUI pixels, as the game scales it. */
    val screenWidth: Int
        get() = scaled().scaledWidth

    /** The screen's height in GUI pixels, as the game scales it. */
    val screenHeight: Int
        get() = scaled().scaledHeight

    private fun scaled(): ScaledResolution {
        val mc = Minecraft.getMinecraft()
        return ScaledResolution(mc, mc.displayWidth, mc.displayHeight)
    }

    /** The game's button click, for every control here that acts on a click (ModularUI's own buttons make it too). */
    fun click() = Interactable.playButtonClickSound()

    /**
     * Draws [block] over everything: no depth test, and no lighting (item rendering before it can leave lighting on,
     * which dims flat colours). For a panel such as a tip.
     */
    inline fun onTop(block: () -> Unit) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT or GL11.GL_DEPTH_BUFFER_BIT or GL11.GL_LIGHTING_BIT)
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST)
            GL11.glDisable(GL11.GL_LIGHTING)
            GL11.glDisable(GL11.GL_COLOR_MATERIAL)
            block()
        } finally {
            GL11.glPopAttrib()
        }
    }

    // region Fading: a whole drawing see-through

    /**
     * How far everything drawn now is faded out, 0 (as drawn) to 1 (gone): multiplies the alpha of every colour, text,
     * picture and item drawn here, and of what is batched while it is set. For a part fading in and out as a whole.
     * Set it around the drawing and put it back to 0 after.
     */
    var fadeOut = 0f

    /** Items and fluids drawn solid however faded, instead of dropping out. */
    var keepItems = false

    /** A colour with the fade applied. */
    fun faded(argb: Int): Int {
        if (fadeOut <= 0f) return argb
        val a = ((argb ushr 24) * (1 - min(1f, fadeOut))).roundToInt()
        return a shl 24 or (argb and 0xFFFFFF)
    }

    /** The fade as an alpha to multiply by. */
    private val kept: Float
        get() = 1 - fadeOut.coerceIn(0f, 1f)

    /** Whether items and fluids still show at the current fade. */
    private val itemsShow: Boolean
        get() = fadeOut < FADE_ITEMS || keepItems

    // endregion

    // region Batching: many flat shapes in one draw call

    @PublishedApi
    internal var batchOpen = false
    private var batchXY = FloatArray(6144)
    private var batchColor = IntArray(3072)
    private var batchCount = 0

    /** Whether a batch is open, so a drawing made of parts joins it rather than drawing its own. */
    val batching: Boolean
        get() = batchOpen

    /**
     * Draws the flat shapes [draw] makes ([rect], [triangle] and everything built of them) in one draw call, in order,
     * or into the batch already open. For layers of many shapes, where a draw call apiece is most of the frame.
     */
    inline fun batch(draw: () -> Unit) {
        val own = !batchOpen
        if (own) beginBatch()
        draw()
        if (own) endBatch()
    }

    @PublishedApi
    internal fun beginBatch() {
        batchOpen = true
        batchCount = 0
    }

    @PublishedApi
    internal fun endBatch() {
        batchOpen = false
        val n = batchCount
        if (n == 0) return
        val xy = batchXY
        val colors = batchColor
        Platform.setupDrawColor()
        Platform.startDrawing(Platform.DrawMode.TRIANGLES, Platform.VertexFormat.POS_COLOR) { buf ->
            for (i in 0 until n) {
                val c = colors[i]
                buf.pos(xy[2 * i].toDouble(), xy[2 * i + 1].toDouble(), 0.0)
                    .color(c shr 16 and 0xFF, c shr 8 and 0xFF, c and 0xFF, c ushr 24)
                    .endVertex()
            }
        }
        Platform.endDrawColor()
    }

    /** One corner of a batched triangle, its colour already faded. */
    private fun vertex(x: Float, y: Float, argb: Int) {
        if (batchCount == batchColor.size) {
            batchColor = batchColor.copyOf(batchCount * 2)
            batchXY = batchXY.copyOf(batchCount * 4)
        }
        batchXY[2 * batchCount] = x
        batchXY[2 * batchCount + 1] = y
        batchColor[batchCount++] = argb
    }

    // endregion

    // region Shapes

    fun rect(x: Float, y: Float, w: Float, h: Float, color: Int) {
        val argb = faded(color)
        if (fadeOut > 0 && argb ushr 24 == 0) return
        if (batchOpen) {
            vertex(x, y, argb)
            vertex(x, y + h, argb)
            vertex(x + w, y + h, argb)
            vertex(x, y, argb)
            vertex(x + w, y + h, argb)
            vertex(x + w, y, argb)
            return
        }
        GuiDraw.drawRect(x, y, w, h, argb)
    }

    fun rect(x: Int, y: Int, w: Int, h: Int, color: Int) =
        rect(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), color)

    /** Raised bevel: fill, light band top-left, dark band bottom-right, an optional 1 px outer [edge] (0 for none). */
    fun bevel(x: Float, y: Float, w: Float, h: Float, fill: Int, light: Int, dark: Int, edge: Int = 0, band: Float = 1f) =
        batch {
            if (edge != 0) rect(x - 1, y - 1, w + 2, h + 2, edge)
            rect(x, y, w, h, fill)
            rect(x, y, w, band, light)
            rect(x, y, band, h, light)
            rect(x, y + h - band, w, band, dark)
            rect(x + w - band, y, band, h, dark)
        }

    fun bevel(x: Int, y: Int, w: Int, h: Int, fill: Int, light: Int, dark: Int, edge: Int = 0, band: Int = 1) =
        bevel(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), fill, light, dark, edge, band.toFloat())

    /** A key: the raised face every button wears, a step lighter under the mouse. */
    fun key(x: Float, y: Float, w: Float, h: Float, hot: Boolean) =
        bevel(x, y, w, h, if (hot) KEY_HOVER else KEY, KEY_HI, KEY_LO)

    fun key(x: Int, y: Int, w: Int, h: Int, hot: Boolean) = key(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), hot)

    /**
     * A filled rectangle with round corners of radius [r], in whole-pixel steps like the game's own art: rows of
     * rectangles at the top and bottom, one block between. [top] and [bottom] pick which corners are round.
     */
    fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float, argb: Int, top: Boolean = true, bottom: Boolean = true) =
        batch {
            val rows = if (r <= 0) 0 else min(ceil(r), floor(h / 2)).toInt()
            for (i in 0 until rows) {
                val dy = r - i - 0.5f
                val inset = (r - sqrt(max(0f, r * r - dy * dy))).roundToInt().toFloat()
                val topIn = if (top) inset else 0f
                val bottomIn = if (bottom) inset else 0f
                rect(x + topIn, y + i, w - 2 * topIn, 1f, argb)
                rect(x + bottomIn, y + h - 1 - i, w - 2 * bottomIn, 1f, argb)
            }
            rect(x, y + rows, w, h - 2 * rows, argb)
        }

    fun roundRect(x: Int, y: Int, w: Int, h: Int, r: Int, argb: Int, top: Boolean = true, bottom: Boolean = true) =
        roundRect(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), r.toFloat(), argb, top, bottom)

    /** A box with its corners off: a pixel's rounding, the face of a chip or a switch. */
    fun pill(x: Float, y: Float, w: Float, h: Float, color: Int) = batch {
        rect(x + 1, y, w - 2, h, color)
        rect(x, y + 1, 1f, h - 2, color)
        rect(x + w - 1, y + 1, 1f, h - 2, color)
    }

    fun pill(x: Int, y: Int, w: Int, h: Int, color: Int) = pill(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), color)

    /**
     * A chip: picked, an edge in [colour] on a face tinted with it; otherwise a quiet face, its edge lighter under the
     * mouse.
     */
    fun chip(x: Float, y: Float, w: Float, h: Float, colour: Int, picked: Boolean, hot: Boolean) {
        val edge = when {
            picked -> colour
            hot -> CHIP_EDGE_HOVER
            else -> CHIP_EDGE
        }
        val fill = when {
            picked -> colour.mixedWith(SURFACE, 0.16f)
            hot -> CONTROL_HOVER
            else -> CONTROL
        }
        batch {
            pill(x, y, w, h, edge)
            pill(x + 1, y + 1, w - 2, h - 2, fill)
        }
    }

    fun chip(x: Int, y: Int, w: Int, h: Int, colour: Int, picked: Boolean, hot: Boolean) =
        chip(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), colour, picked, hot)

    /** A text box: a dark face in a hard edge, the edge gold while it is written in. */
    fun field(x: Float, y: Float, w: Float, h: Float, focused: Boolean) = batch {
        rect(x, y, w, h, if (focused) GOLD else KEY_EDGE)
        rect(x + 1, y + 1, w - 2, h - 2, FIELD)
    }

    fun field(x: Int, y: Int, w: Int, h: Int, focused: Boolean) =
        field(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), focused)

    /** Sunken well: dark top-left, light bottom-right. */
    fun well(x: Float, y: Float, w: Float, h: Float, fill: Int, dark: Int, light: Int) = batch {
        rect(x, y, w, h, fill)
        rect(x, y, w, 1f, dark)
        rect(x, y, 1f, h, dark)
        rect(x, y + h - 1, w, 1f, light)
        rect(x + w - 1, y, 1f, h, light)
    }

    fun well(x: Int, y: Int, w: Int, h: Int, fill: Int, dark: Int, light: Int) =
        well(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), fill, dark, light)

    /** Factory Flow's card window: a frame fill in a ring, 1 px highlight and shadow bands inside it. */
    fun cardFrame(x: Float, y: Float, w: Float, h: Float) = batch {
        rect(x, y, w, h, RING)
        rect(x + 1, y + 1, w - 2, h - 2, FRAME)
        rect(x + 1, y + 1, w - 2, 1f, HIGHLIGHT)
        rect(x + 1, y + 1, 1f, h - 2, HIGHLIGHT)
        rect(x + 1, y + h - 2, w - 2, 1f, SHADOW)
        rect(x + w - 2, y + 1, 1f, h - 2, SHADOW)
    }

    fun cardFrame(x: Int, y: Int, w: Int, h: Int) = cardFrame(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat())

    /** A Factory Flow stat or setting tile: a tile fill with a 1 px light and dark inner edge. */
    fun tile(x: Float, y: Float, w: Float, h: Float) = batch {
        rect(x, y, w, h, TILE_EDGE)
        rect(x + 1, y + 1, w - 2, h - 2, TILE)
        rect(x + 1, y + 1, w - 2, 1f, TILE_HI)
        rect(x + 1, y + 1, 1f, h - 2, TILE_HI)
    }

    fun tile(x: Int, y: Int, w: Int, h: Int) = tile(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat())

    /**
     * The shadow a card casts, as Factory Flow's (6 right, 8 down, 7 soft, 45% at its heart): four stacked translucent
     * rectangles, each a little larger, so the edge fades out.
     */
    fun dropShadow(x: Float, y: Float, w: Float, h: Float) = batch {
        for (i in 3 downTo 0) {
            val grow = i * 2f
            rect(x + 6 - grow, y + 8 - grow, w + 2 * grow, h + 2 * grow, 0x18000000)
        }
    }

    fun dropShadow(x: Int, y: Int, w: Int, h: Int) = dropShadow(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat())

    /** A filled triangle in the current (possibly scaled) space. */
    fun triangle(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, argb: Int) {
        val c = faded(argb)
        if (batchOpen) {
            vertex(x1, y1, c)
            vertex(x2, y2, c)
            vertex(x3, y3, c)
            vertex(x1, y1, c)
            vertex(x3, y3, c)
            vertex(x2, y2, c)
            return
        }
        val a = c ushr 24
        val r = c shr 16 and 0xFF
        val g = c shr 8 and 0xFF
        val b = c and 0xFF
        // Through ModularUI's own drawing path, so it lands at the same depth and GL state as its rectangles.
        Platform.setupDrawColor()
        Platform.startDrawing(Platform.DrawMode.TRIANGLES, Platform.VertexFormat.POS_COLOR) { buf ->
            buf.pos(x1.toDouble(), y1.toDouble(), 0.0).color(r, g, b, a).endVertex()
            buf.pos(x2.toDouble(), y2.toDouble(), 0.0).color(r, g, b, a).endVertex()
            buf.pos(x3.toDouble(), y3.toDouble(), 0.0).color(r, g, b, a).endVertex()
            // And the other winding, so face culling cannot drop it whichever way it points.
            buf.pos(x1.toDouble(), y1.toDouble(), 0.0).color(r, g, b, a).endVertex()
            buf.pos(x3.toDouble(), y3.toDouble(), 0.0).color(r, g, b, a).endVertex()
            buf.pos(x2.toDouble(), y2.toDouble(), 0.0).color(r, g, b, a).endVertex()
        }
        Platform.endDrawColor()
    }

    /** A ring of [t] px just outside a box, for selection and warnings. */
    fun ring(x: Float, y: Float, w: Float, h: Float, t: Float, color: Int) = batch {
        rect(x - t, y - t, w + 2 * t, t, color)
        rect(x - t, y + h, w + 2 * t, t, color)
        rect(x - t, y, t, h, color)
        rect(x + w, y, t, h, color)
    }

    fun ring(x: Int, y: Int, w: Int, h: Int, t: Int, color: Int) =
        ring(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), t.toFloat(), color)

    /** A dashed rectangle outline with dashes [on] long and gaps [off] long. */
    fun dashed(x: Float, y: Float, w: Float, h: Float, color: Int, on: Int = 2, off: Int = 2) = batch {
        var d = 0f
        while (d < w) {
            rect(x + d, y, min(on.toFloat(), w - d), 1f, color)
            rect(x + d, y + h - 1, min(on.toFloat(), w - d), 1f, color)
            d += on + off
        }
        d = 0f
        while (d < h) {
            rect(x, y + d, 1f, min(on.toFloat(), h - d), color)
            rect(x + w - 1, y + d, 1f, min(on.toFloat(), h - d), color)
            d += on + off
        }
    }

    fun dashed(x: Int, y: Int, w: Int, h: Int, color: Int, on: Int = 2, off: Int = 2) =
        dashed(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), color, on, off)

    /** NEI's 18x18 slot, grey for items and dark for fluids. */
    fun slot(x: Float, y: Float, fluid: Boolean) =
        if (fluid) well(x, y, 18f, 18f, SLOT_FLUID, SLOT_FLUID_TL, SLOT_FLUID_BR)
        else well(x, y, 18f, 18f, SLOT_ITEM, SLOT_ITEM_TL, SLOT_ITEM_BR)

    fun slot(x: Int, y: Int, fluid: Boolean) = slot(x.toFloat(), y.toFloat(), fluid)

    // endregion

    // region Pictures

    /**
     * A whole texture (a bundled PNG) drawn into a rectangle: as it is, or with [tint] in one colour by its shape alone
     * (e.g. black at 45% for the shadow it casts).
     */
    fun texture(loc: ResourceLocation, x: Float, y: Float, w: Float, h: Float, tint: Int? = null) {
        Minecraft.getMinecraft().textureManager.bindTexture(loc)
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glEnable(GL11.GL_BLEND)
        OpenGlHelper.glBlendFunc(770, 771, 1, 0)
        if (tint == null) {
            GL11.glColor4f(1f, 1f, 1f, kept)
        } else {
            // Drawn once, the colour multiplied in: black keeps the texture's alpha and loses its colours.
            GL11.glColor4f(
                (tint shr 16 and 0xFF) / 255f,
                (tint shr 8 and 0xFF) / 255f,
                (tint and 0xFF) / 255f,
                (tint ushr 24) / 255f * kept,
            )
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE)
        }
        val t = Tessellator.instance
        t.startDrawingQuads()
        t.addVertexWithUV(x.toDouble(), (y + h).toDouble(), 0.0, 0.0, 1.0)
        t.addVertexWithUV((x + w).toDouble(), (y + h).toDouble(), 0.0, 1.0, 1.0)
        t.addVertexWithUV((x + w).toDouble(), y.toDouble(), 0.0, 1.0, 0.0)
        t.addVertexWithUV(x.toDouble(), y.toDouble(), 0.0, 0.0, 0.0)
        t.draw()
        if (tint != null) GL11.glColor4f(1f, 1f, 1f, 1f)
    }

    /**
     * An item or a fluid as Factory Flow shows one: bare, no slot, with a soft shadow under it. A fluid is a slightly
     * smaller square, as its sprite fills its box where an item's art does not.
     */
    fun icon(item: ItemStack?, fluid: FluidStack?, x: Float, y: Float, size: Float = 16f, z: Float = 0f) {
        when {
            fluid != null -> {
                val inset = size / 12f
                val side = size - 2 * inset
                iconShadow(null, fluid, x + inset, y + inset, side)
                this.fluid(fluid, x + inset, y + inset, side, z)
            }
            item != null -> {
                iconShadow(item, null, x, y, size)
                this.item(item, x, y, size, z)
            }
        }
    }

    fun icon(item: ItemStack?, fluid: FluidStack?, x: Int, y: Int, size: Int = 16, z: Int = 0) =
        icon(item, fluid, x.toFloat(), y.toFloat(), size.toFloat(), z.toFloat())

    /** The shadow alone, under the rectangle the icon fills: its silhouette, dropped and softened by spreading. */
    fun iconShadow(item: ItemStack?, fluid: FluidStack?, x: Float, y: Float, size: Float) {
        // Faded with the drawing, and gone with the icon once it drops out.
        if (!itemsShow) return
        // Three spreads, the widest faintest: about two fifths black at the heart, fading over two pixels.
        val dy = size / 8f
        for (i in 0 until 3) {
            val g = SHADOW_GROW[i] * size / 24f
            when {
                fluid != null -> rect(x - g, y + dy - g, size + 2 * g, size + 2 * g, SHADOW_SHADE[i])
                item != null -> IconShadows.draw(item, x - g, y + dy - g, size + 2 * g, faded(SHADOW_SHADE[i]))
            }
        }
    }

    /** An item drawn by the game; a fading drawing drops it once it is more than a third gone. */
    fun item(stack: ItemStack?, x: Float, y: Float, size: Float = 16f, z: Float = 0f) {
        // The game draws items with no alpha of their own, whatever the blending, so they drop out a little ahead.
        if (stack != null && itemsShow) GuiDraw.drawItem(stack, x.toInt(), y.toInt(), size, size, z.toInt())
    }

    fun item(stack: ItemStack?, x: Int, y: Int, size: Int = 16, z: Int = 0) =
        item(stack, x.toFloat(), y.toFloat(), size.toFloat(), z.toFloat())

    fun fluid(stack: FluidStack?, x: Float, y: Float, size: Float = 16f, z: Float = 0f) {
        if (stack != null && itemsShow) GuiDraw.drawFluidTexture(stack, x, y, size, size, z)
    }

    // endregion

    // region Text

    /** Text, faded as everything is, at [scale] and with or without its shadow. */
    fun text(s: String, x: Float, y: Float, color: Int, scale: Float = 1f, shadow: Boolean = true) {
        val c = faded(color)
        // The game's font takes an alpha under 4 as none at all, and draws it solid.
        if (fadeOut > 0 && (c ushr 24) < 8) return
        GuiDraw.drawText(s, x, y, scale, c, shadow)
    }

    fun text(s: String, x: Int, y: Int, color: Int, scale: Float = 1f, shadow: Boolean = true) =
        text(s, x.toFloat(), y.toFloat(), color, scale, shadow)

    fun textRight(s: String, right: Float, y: Float, color: Int) = text(s, right - s.guiWidth, y, color)

    fun textRight(s: String, right: Int, y: Int, color: Int) = textRight(s, right.toFloat(), y.toFloat(), color)

    fun textCentered(s: String, cx: Float, y: Float, color: Int) = text(s, cx - s.guiWidth / 2f, y, color)

    fun textCentered(s: String, cx: Int, y: Int, color: Int) = textCentered(s, cx.toFloat(), y.toFloat(), color)

    // endregion
}
