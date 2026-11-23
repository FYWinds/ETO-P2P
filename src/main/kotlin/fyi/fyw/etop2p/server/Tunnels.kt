package fyi.fyw.etop2p.server

import appeng.api.config.SecurityPermissions
import appeng.api.networking.IGrid
import appeng.api.networking.IGridHost
import appeng.api.networking.security.ISecurityGrid
import appeng.api.parts.IPartHost
import appeng.api.parts.PartItemStack
import appeng.me.GridNode
import appeng.parts.p2p.PartP2PTunnel
import appeng.parts.p2p.PartP2PTunnelME
import fyi.fyw.etop2p.data.P2PEntry
import fyi.fyw.etop2p.data.P2PKey
import fyi.fyw.etop2p.data.P2PTypes
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.Item
import net.minecraftforge.common.DimensionManager
import net.minecraftforge.common.util.ForgeDirection

/** Reading tunnels out of the world and a network. */
internal object Tunnels {

    fun keyOf(tunnel: PartP2PTunnel<*>): P2PKey {
        val tile = tunnel.host.tile
        return P2PKey(tile.worldObj.provider.dimensionId, tile.xCoord, tile.yCoord, tile.zCoord, tunnel.side.ordinal)
    }

    fun typeOf(tunnel: PartP2PTunnel<*>): Int = P2PTypes.of(tunnel.getItemStack(PartItemStack.Network))

    fun nameOf(tunnel: PartP2PTunnel<*>): String = if (tunnel.hasCustomName()) tunnel.customName else ""

    /**
     * Names this one tunnel. AE2's own renaming goes through the connection's input to its outputs and skips them
     * all while the tunnel has no channel, which is the case just after it was converted.
     */
    fun rename(tunnel: PartP2PTunnel<*>, name: String) {
        val stack = tunnel.getItemStack(PartItemStack.Wrench) ?: return
        if (name.isEmpty()) stack.func_135074_t() else stack.setStackDisplayName(name)
        tunnel.host?.markForSave()
    }

    /** The tunnel at [key] while its block is loaded. */
    fun at(key: P2PKey): PartP2PTunnel<*>? {
        val world = DimensionManager.getWorld(key.dim) ?: return null
        if (!world.blockExists(key.x, key.y, key.z)) return null
        val host = world.getTileEntity(key.x, key.y, key.z) as? IPartHost ?: return null
        return host.getPart(ForgeDirection.getOrientation(key.side)) as? PartP2PTunnel<*>
    }

    fun gridOf(tunnel: PartP2PTunnel<*>): IGrid? = tunnel.gridNode?.grid

    /** Every tunnel of the network. */
    fun scan(grid: IGrid): Map<P2PKey, PartP2PTunnel<*>> {
        val found = HashMap<P2PKey, PartP2PTunnel<*>>()
        for (kind in grid.machinesClasses) {
            if (!PartP2PTunnel::class.java.isAssignableFrom(kind)) continue
            for (node in grid.getMachines(kind)) {
                val tunnel = node.machine as? PartP2PTunnel<*> ?: continue
                if (tunnel.host?.tile?.worldObj != null) found[keyOf(tunnel)] = tunnel
            }
        }
        return found
    }

    /** AE2's rule for changing a network's parts: the security terminal's build permission, where there is one. */
    fun mayBuild(grid: IGrid, player: EntityPlayer): Boolean =
        grid.getCache<ISecurityGrid>(ISecurityGrid::class.java).hasPermission(player, SecurityPermissions.BUILD)

    fun entry(key: P2PKey, tunnel: PartP2PTunnel<*>, label: String): P2PEntry {
        val (item, meta) = attached(tunnel)
        return P2PEntry(
            key = key,
            type = typeOf(tunnel),
            name = nameOf(tunnel),
            label = label,
            frequency = tunnel.frequency,
            output = tunnel.isOutput,
            online = tunnel.isPowered && tunnel.isActive,
            channels = if (tunnel is PartP2PTunnelME) (tunnel.externalFacingNode as? GridNode)?.usedChannels() ?: 0 else -1,
            attachedItem = item,
            attachedMeta = meta,
        )
    }

    /** The item of what the tunnel faces: the part on the touching side of a cable bus there, else the block. */
    private fun attached(tunnel: PartP2PTunnel<*>): Pair<Int, Int> {
        val tile = tunnel.host.tile
        val side = tunnel.side
        val world = tile.worldObj
        val x = tile.xCoord + side.offsetX
        val y = tile.yCoord + side.offsetY
        val z = tile.zCoord + side.offsetZ
        if (!world.blockExists(x, y, z)) return NOTHING
        val block = world.getBlock(x, y, z)
        if (block.isAir(world, x, y, z)) return NOTHING
        val there = world.getTileEntity(x, y, z)
        if (there is IPartHost) {
            val stack = there.getPart(side.opposite)?.getItemStack(PartItemStack.Network)
                ?: there.getPart(ForgeDirection.UNKNOWN)?.getItemStack(PartItemStack.Network)
            if (stack != null) return Item.getIdFromItem(stack.item) to stack.itemDamage
        }
        val item = Item.getItemFromBlock(block) ?: return NOTHING
        return Item.getIdFromItem(item) to block.getDamageValue(world, x, y, z)
    }

    private val NOTHING = -1 to 0

    /** The network a block's node on [side] belongs to, trying the block's other nodes when that side has none. */
    fun gridAt(dim: Int, x: Int, y: Int, z: Int, side: Int): IGrid? {
        val world = DimensionManager.getWorld(dim) ?: return null
        if (!world.blockExists(x, y, z)) return null
        val host = world.getTileEntity(x, y, z) as? IGridHost ?: return null
        val first = ForgeDirection.getOrientation(side)
        return (sequenceOf(first, ForgeDirection.UNKNOWN) + ForgeDirection.VALID_DIRECTIONS.asSequence())
            .mapNotNull { host.getGridNode(it)?.grid }
            .firstOrNull()
    }
}
