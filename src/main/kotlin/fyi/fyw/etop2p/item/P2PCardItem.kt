package fyi.fyw.etop2p.item

import appeng.api.networking.IGridHost
import appeng.api.parts.IPartHost
import appeng.parts.p2p.PartP2PTunnel
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import fyi.fyw.etop2p.data.Op
import fyi.fyw.etop2p.data.P2PKey
import fyi.fyw.etop2p.server.P2POps
import fyi.fyw.etop2p.server.Sessions
import fyi.fyw.etop2p.server.Tunnels
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.ChatComponentTranslation
import net.minecraft.util.EnumChatFormatting
import net.minecraft.util.StatCollector
import net.minecraft.util.Vec3
import net.minecraft.world.World

/**
 * The card. Used on a network it opens the screen of that network's tunnels; used on air, the network it was last
 * used on. Without the screen: sneaking and using it on a tunnel picks that tunnel as the input, and using it on
 * other tunnels then binds each as an output of it; sneaking and using it on air lets the input go.
 */
object P2PCardItem : Item() {

    private const val NETWORK = "net"
    private const val ARMED = "armed"

    init {
        maxStackSize = 1
        unlocalizedName = "etop2p.card"
        setTextureName("etop2p:p2p_card")
        creativeTab = CreativeTabs.tabTools
    }

    /** Before the block's own use, which for most network parts opens their screen. Decided on the server. */
    override fun onItemUseFirst(
        stack: ItemStack, player: EntityPlayer, world: World, x: Int, y: Int, z: Int, side: Int,
        hitX: Float, hitY: Float, hitZ: Float,
    ): Boolean {
        if (world.isRemote || player !is EntityPlayerMP) return false
        val host = world.getTileEntity(x, y, z) as? IGridHost ?: return false
        val hit = Vec3.createVectorHelper(hitX.toDouble(), hitY.toDouble(), hitZ.toDouble())
        val tunnel = (host as? IPartHost)?.selectPart(hit)?.part as? PartP2PTunnel<*>
        val dim = world.provider.dimensionId
        val grid = tunnel?.let(Tunnels::gridOf) ?: Tunnels.gridAt(dim, x, y, z, side) ?: return false
        if (!Tunnels.mayBuild(grid, player)) {
            say(player, "etop2p.notice.denied", error = true)
            return true
        }
        write(stack, NETWORK, P2PKey(dim, x, y, z, side))
        val key = tunnel?.let(Tunnels::keyOf)
        val armed = read(stack, ARMED)
        when {
            key != null && player.isSneaking -> {
                write(stack, ARMED, key)
                say(player, "etop2p.quick.armed", key.x, key.y, key.z)
            }
            key != null && armed != null && armed != key -> {
                val ops = P2POps(player, grid)
                val targets = ops.plan(Op.Bind(armed, listOf(key)), Tunnels.scan(grid))
                val bound = targets.isNotEmpty() && ops.apply(targets) == targets
                if (bound) say(player, "etop2p.quick.bound", key.x, key.y, key.z)
                else say(player, "etop2p.quick.failed", error = true)
            }
            else -> Sessions.open(player, grid, key)
        }
        return true
    }

    /**
     * On the client, so that a use on a network is not followed by a use on air (which the game sends when nothing
     * took the click, and which would open the screen a second time).
     */
    override fun onItemUse(
        stack: ItemStack, player: EntityPlayer, world: World, x: Int, y: Int, z: Int, side: Int,
        hitX: Float, hitY: Float, hitZ: Float,
    ): Boolean = world.isRemote && world.getTileEntity(x, y, z) is IGridHost

    override fun onItemRightClick(stack: ItemStack, world: World, player: EntityPlayer): ItemStack {
        if (world.isRemote || player !is EntityPlayerMP) return stack
        if (player.isSneaking) {
            if (read(stack, ARMED) != null) {
                stack.tagCompound?.removeTag(ARMED)
                say(player, "etop2p.quick.cleared")
            }
            return stack
        }
        val last = read(stack, NETWORK)
        val grid = last?.let { Tunnels.gridAt(it.dim, it.x, it.y, it.z, it.side) }
        when {
            last == null -> say(player, "etop2p.notice.no_network", error = true)
            grid == null -> say(player, "etop2p.notice.unloaded", last.x, last.y, last.z, error = true)
            !Tunnels.mayBuild(grid, player) -> say(player, "etop2p.notice.denied", error = true)
            else -> Sessions.open(player, grid, null)
        }
        return stack
    }

    @SideOnly(Side.CLIENT)
    override fun addInformation(stack: ItemStack, player: EntityPlayer, lines: MutableList<String>, advanced: Boolean) {
        read(stack, ARMED)?.let {
            lines += EnumChatFormatting.GREEN.toString() +
                StatCollector.translateToLocalFormatted("etop2p.tooltip.armed", it.x, it.y, it.z)
        }
        read(stack, NETWORK)?.let {
            lines += EnumChatFormatting.GRAY.toString() +
                StatCollector.translateToLocalFormatted("etop2p.tooltip.network", it.x, it.y, it.z, it.dim)
        }
        lines += EnumChatFormatting.DARK_GRAY.toString() + StatCollector.translateToLocal("etop2p.tooltip.use")
        lines += EnumChatFormatting.DARK_GRAY.toString() + StatCollector.translateToLocal("etop2p.tooltip.quick")
    }

    private fun say(player: EntityPlayer, key: String, vararg args: Any, error: Boolean = false) {
        val line = ChatComponentTranslation(key, *args)
        line.chatStyle.color = if (error) EnumChatFormatting.RED else EnumChatFormatting.GRAY
        player.addChatMessage(line)
    }

    private fun read(stack: ItemStack, name: String): P2PKey? {
        val tag = stack.tagCompound?.takeIf { it.hasKey(name) }?.getCompoundTag(name) ?: return null
        return P2PKey(
            tag.getInteger("dim"), tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z"),
            tag.getByte("side").toInt(),
        )
    }

    private fun write(stack: ItemStack, name: String, key: P2PKey) {
        val root = stack.tagCompound ?: NBTTagCompound().also { stack.tagCompound = it }
        root.setTag(
            name,
            NBTTagCompound().apply {
                setInteger("dim", key.dim)
                setInteger("x", key.x)
                setInteger("y", key.y)
                setInteger("z", key.z)
                setByte("side", key.side.toByte())
            },
        )
    }
}
