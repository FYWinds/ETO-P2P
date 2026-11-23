package fyi.fyw.etop2p.client

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import fyi.fyw.etolib.ui.UiText.tr
import fyi.fyw.etop2p.data.P2PKey
import kotlin.math.atan2
import kotlin.math.sqrt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.Tessellator
import net.minecraftforge.client.event.RenderGameOverlayEvent
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.common.util.ForgeDirection
import org.lwjgl.opengl.GL11

/**
 * Tunnels pointed out in the world: an outline seen through walls, a line from an input to each of its outputs,
 * and for the one asked for an arrow with its distance at the top of the screen. Only tunnels of the player's
 * dimension are drawn. It ends once the player has stood by the one asked for a moment, after [LASTS_MS] at the
 * latest, and when the card's screen opens again or the card is used on air while sneaking.
 */
object Highlights {

    class Mark(val key: P2PKey, val color: Int, val title: String)

    private const val LASTS_MS = 30_000L

    /** Within this many blocks of the tunnel asked for the player has found it; the marks stay [FOUND_MS] more. */
    private const val FOUND_WITHIN = 3.0
    private const val FOUND_MS = 3_000L

    private var marks: List<Mark> = emptyList()
    private var lines: List<Pair<P2PKey, P2PKey>> = emptyList()
    private var target: Mark? = null
    private var until = 0L

    val active: Boolean get() = marks.isNotEmpty() && System.currentTimeMillis() < until

    /** Points [marks] out, with [lines] between them; the arrow goes to [target]. */
    fun show(marks: List<Mark>, lines: List<Pair<P2PKey, P2PKey>>, target: Mark?) {
        this.marks = marks
        this.lines = lines
        this.target = target
        until = System.currentTimeMillis() + LASTS_MS
    }

    fun clear() {
        marks = emptyList()
        lines = emptyList()
        target = null
    }

    /** The middle of the tunnel's plate on its block face. */
    private fun centre(key: P2PKey): DoubleArray {
        val side = ForgeDirection.getOrientation(key.side)
        return doubleArrayOf(key.x + 0.5 + side.offsetX * 0.4, key.y + 0.5 + side.offsetY * 0.4, key.z + 0.5 + side.offsetZ * 0.4)
    }

    @SubscribeEvent
    fun onRenderWorld(event: RenderWorldLastEvent) {
        if (!active) return
        val mc = Minecraft.getMinecraft()
        val viewer = mc.renderViewEntity ?: return
        val dim = mc.theWorld?.provider?.dimensionId ?: return
        val t = event.partialTicks.toDouble()
        val vx = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * t
        val vy = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * t
        val vz = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * t

        // Standing by the tunnel asked for cuts the time left down to a moment; walking off again does not give it back.
        target?.key?.takeIf { it.dim == dim }?.let(::centre)?.let { c ->
            val dx = c[0] - vx
            val dy = c[1] - vy
            val dz = c[2] - vz
            if (dx * dx + dy * dy + dz * dz <= FOUND_WITHIN * FOUND_WITHIN) until = minOf(until, System.currentTimeMillis() + FOUND_MS)
        }

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT or GL11.GL_LINE_BIT or GL11.GL_CURRENT_BIT)
        GL11.glPushMatrix()
        GL11.glTranslated(-vx, -vy, -vz)
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        GL11.glDisable(GL11.GL_LIGHTING)
        GL11.glDisable(GL11.GL_DEPTH_TEST)
        GL11.glDisable(GL11.GL_CULL_FACE)
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        val tessellator = Tessellator.instance

        GL11.glLineWidth(1.5f)
        tessellator.startDrawing(GL11.GL_LINES)
        for ((from, to) in lines) {
            if (from.dim != dim || to.dim != dim) continue
            val a = centre(from)
            val b = centre(to)
            val color = marks.firstOrNull { it.key == from }?.color ?: 0xFFFFFF
            tessellator.setColorRGBA_I(color and 0xFFFFFF, 150)
            tessellator.addVertex(a[0], a[1], a[2])
            tessellator.addVertex(b[0], b[1], b[2])
        }
        tessellator.draw()

        GL11.glLineWidth(2.5f)
        for (mark in marks) {
            if (mark.key.dim != dim) continue
            box(tessellator, mark.key, mark.color and 0xFFFFFF)
        }

        GL11.glPopMatrix()
        GL11.glPopAttrib()
    }

    /** The tunnel's plate: a slab on its face, filled faintly and outlined. */
    private fun box(tessellator: Tessellator, key: P2PKey, rgb: Int) {
        val side = ForgeDirection.getOrientation(key.side)
        val c = centre(key)
        // Thin along the side's axis, wide across it.
        val hx = if (side.offsetX != 0) 0.12 else 0.32
        val hy = if (side.offsetY != 0) 0.12 else 0.32
        val hz = if (side.offsetZ != 0) 0.12 else 0.32
        val x0 = c[0] - hx
        val x1 = c[0] + hx
        val y0 = c[1] - hy
        val y1 = c[1] + hy
        val z0 = c[2] - hz
        val z1 = c[2] + hz
        val corners = arrayOf(
            doubleArrayOf(x0, y0, z0), doubleArrayOf(x1, y0, z0), doubleArrayOf(x1, y0, z1), doubleArrayOf(x0, y0, z1),
            doubleArrayOf(x0, y1, z0), doubleArrayOf(x1, y1, z0), doubleArrayOf(x1, y1, z1), doubleArrayOf(x0, y1, z1),
        )
        val faces = arrayOf(
            intArrayOf(0, 1, 2, 3), intArrayOf(4, 5, 6, 7), intArrayOf(0, 1, 5, 4),
            intArrayOf(3, 2, 6, 7), intArrayOf(0, 3, 7, 4), intArrayOf(1, 2, 6, 5),
        )
        tessellator.startDrawingQuads()
        tessellator.setColorRGBA_I(rgb, 60)
        for (face in faces) for (i in face) tessellator.addVertex(corners[i][0], corners[i][1], corners[i][2])
        tessellator.draw()
        val edges = arrayOf(0 to 1, 1 to 2, 2 to 3, 3 to 0, 4 to 5, 5 to 6, 6 to 7, 7 to 4, 0 to 4, 1 to 5, 2 to 6, 3 to 7)
        tessellator.startDrawing(GL11.GL_LINES)
        tessellator.setColorRGBA_I(rgb, 255)
        for ((a, b) in edges) {
            tessellator.addVertex(corners[a][0], corners[a][1], corners[a][2])
            tessellator.addVertex(corners[b][0], corners[b][1], corners[b][2])
        }
        tessellator.draw()
    }

    /** At the top of the screen: an arrow turned towards the target as seen from above, its title and distance. */
    @SubscribeEvent
    fun onOverlay(event: RenderGameOverlayEvent.Post) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || !active) return
        val mark = target ?: return
        val mc = Minecraft.getMinecraft()
        val player = mc.thePlayer ?: return
        if (mc.currentScreen != null || mc.theWorld?.provider?.dimensionId != mark.key.dim) return
        val c = centre(mark.key)
        val dx = c[0] - player.posX
        val dy = c[1] - (player.posY)
        val dz = c[2] - player.posZ
        val distance = sqrt(dx * dx + dy * dy + dz * dz)
        // Minecraft's yaw 0 looks along +z and grows clockwise seen from above.
        val bearing = Math.toDegrees(atan2(-dx, dz)) - player.rotationYaw
        val font = mc.fontRenderer
        val resolution = ScaledResolution(mc, mc.displayWidth, mc.displayHeight)
        val text = "${mark.title}  ${tr("etop2p.metres", distance.toInt())}" + if (dy > 2) " ↑" else if (dy < -2) " ↓" else ""
        val width = font.getStringWidth(text) + 16
        val left = (resolution.scaledWidth - width) / 2
        val top = 6

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT or GL11.GL_CURRENT_BIT)
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        GL11.glDisable(GL11.GL_CULL_FACE)
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        GL11.glPushMatrix()
        GL11.glTranslatef(left + 5f, top + 4f, 0f)
        GL11.glRotated(bearing, 0.0, 0.0, 1.0)
        val tessellator = Tessellator.instance
        tessellator.startDrawing(GL11.GL_TRIANGLES)
        tessellator.setColorRGBA_I(mark.color and 0xFFFFFF, 255)
        tessellator.addVertex(0.0, -5.0, 0.0)
        tessellator.addVertex(-4.0, 4.0, 0.0)
        tessellator.addVertex(4.0, 4.0, 0.0)
        tessellator.draw()
        GL11.glPopMatrix()
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        font.drawStringWithShadow(text, left + 14, top, 0xFFFFFF)
        GL11.glPopAttrib()
    }
}
