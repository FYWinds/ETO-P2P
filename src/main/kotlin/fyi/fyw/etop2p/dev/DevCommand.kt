package fyi.fyw.etop2p.dev

import appeng.api.AEApi
import appeng.api.parts.IPartHost
import appeng.api.util.AEColor
import fyi.fyw.etop2p.data.Op
import fyi.fyw.etop2p.data.P2PKey
import fyi.fyw.etop2p.data.P2PTypes
import fyi.fyw.etop2p.server.Sessions
import fyi.fyw.etop2p.server.Tunnels
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.command.WrongUsageException
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.server.MinecraftServer
import net.minecraft.util.ChatComponentText
import net.minecraftforge.common.util.ForgeDirection

/**
 * For testing without hands, only with `-Detop2p.dev=true`: builds tunnels, opens a player's screen and runs the
 * screen's operations from the console. Tunnels are written `x,y,z,side` in the player's dimension.
 *
 *     etop2p_dev build <player> <x> <y> <z> <dx> <dz> <count>   a row of cable with one tunnel on top of each
 *     etop2p_dev open <player> <x> <y> <z>                      the screen, on the network of that block
 *     etop2p_dev list <player>                                  the network's tunnels as the server sees them
 *     etop2p_dev bind <player> <input> <output>...
 *     etop2p_dev makeinput|unbind <player> <tunnel>...
 *     etop2p_dev convert <player> <type id> <tunnel>...
 *     etop2p_dev rename|label <player> <text> <tunnel>...
 *     etop2p_dev tag <player> <color> <favorite 0|1> <note> <tunnel>...
 *     etop2p_dev undo <player>
 *     etop2p_dev look <player> <yaw> <pitch>                    turns the player's head, to aim a click
 *     etop2p_dev sneak <player> <0|1>                           as the server sees it, until the client says otherwise
 */
object DevCommand : CommandBase() {

    val enabled: Boolean get() = java.lang.Boolean.getBoolean("etop2p.dev")

    override fun getCommandName() = "etop2p_dev"

    override fun getCommandUsage(sender: ICommandSender) = "/etop2p_dev build|open|list|bind|makeinput|unbind|convert|rename|label|tag|undo <player> ..."

    override fun getRequiredPermissionLevel() = 4

    override fun processCommand(sender: ICommandSender, args: Array<String>) {
        if (args.size < 2) throw WrongUsageException(getCommandUsage(sender))
        val player = MinecraftServer.getServer().configurationManager.func_152612_a(args[1])
            ?: throw WrongUsageException("no such player: ${args[1]}")
        val dim = player.worldObj.provider.dimensionId
        fun key(text: String) = text.split(',').map(String::toInt).let { P2PKey(dim, it[0], it[1], it[2], it[3]) }
        fun keys(from: Int) = args.drop(from).map(::key)
        fun say(line: String) = sender.addChatMessage(ChatComponentText(line))
        when (args[0]) {
            "build" -> say("placed ${build(player, args.drop(2).map(String::toInt))} tunnels")
            "open" -> {
                val (x, y, z) = args.drop(2).map(String::toInt)
                val grid = Tunnels.gridAt(dim, x, y, z, ForgeDirection.UNKNOWN.ordinal) ?: throw WrongUsageException("no network there")
                Sessions.open(player, grid, null)
            }
            "list" -> {
                val grid = Sessions.gridOf(player) ?: throw WrongUsageException("no screen open")
                Tunnels.scan(grid).entries
                    .sortedWith(compareBy({ it.key.x }, { it.key.y }, { it.key.z }, { it.key.side }))
                    .forEach { (k, tunnel) ->
                        val type = P2PTypes.all.getOrNull(Tunnels.typeOf(tunnel))?.id ?: "?"
                        val role = if (tunnel.frequency == 0L) "unbound" else if (tunnel.isOutput) "out" else "in"
                        say("${k.x},${k.y},${k.z},${k.side} $type $role ${java.lang.Long.toHexString(tunnel.frequency)} '${Tunnels.nameOf(tunnel)}'")
                    }
            }
            "bind" -> Sessions.handle(player, Op.Bind(key(args[2]), keys(3)))
            "makeinput" -> keys(2).forEach { Sessions.handle(player, Op.MakeInput(it)) }
            "unbind" -> Sessions.handle(player, Op.Unbind(keys(2)))
            "convert" -> {
                val type = P2PTypes.all.firstOrNull { it.id == args[2] } ?: throw WrongUsageException("types: ${P2PTypes.available.joinToString { it.id }}")
                Sessions.handle(player, Op.Convert(keys(3), type.index))
            }
            "rename" -> Sessions.handle(player, Op.Rename(keys(3).map { it to args[2] }))
            "label" -> Sessions.handle(player, Op.Label(keys(3).map { it to args[2] }))
            "tag" -> {
                val grid = Sessions.gridOf(player) ?: throw WrongUsageException("no screen open")
                val tunnels = Tunnels.scan(grid)
                val frequencies = keys(5).mapNotNull { tunnels[it]?.frequency }
                Sessions.handle(player, Op.Meta(frequencies, args[2].toInt(), args[3] == "1", args[4]))
            }
            "undo" -> Sessions.handle(player, Op.Undo)
            "look" -> player.playerNetServerHandler.setPlayerLocation(
                player.posX, player.posY, player.posZ, args[2].toFloat(), args[3].toFloat(),
            )
            "sneak" -> player.isSneaking = args[2] == "1"
            else -> throw WrongUsageException(getCommandUsage(sender))
        }
    }

    /** A row of fluix cable from x,y,z stepping dx,dz, each block with one tunnel on top, the types taking turns. */
    private fun build(player: EntityPlayerMP, numbers: List<Int>): Int {
        val (x, y, z) = numbers
        val dx = numbers[3]
        val dz = numbers[4]
        val definitions = AEApi.instance().definitions()
        val bus = definitions.blocks().multiPart().maybeBlock().orNull() ?: return 0
        val cable = definitions.parts().cableGlass().stack(AEColor.Transparent, 1)
        val types = P2PTypes.available
        var placed = 0
        for (i in 0 until numbers[5]) {
            val bx = x + dx * i
            val bz = z + dz * i
            player.worldObj.setBlock(bx, y, bz, bus)
            val host = player.worldObj.getTileEntity(bx, y, bz) as? IPartHost ?: continue
            host.addPart(cable.copy(), ForgeDirection.UNKNOWN, player)
            if (host.addPart(types[i % types.size].stack!!.copy(), ForgeDirection.UP, player) != null) placed++
        }
        return placed
    }
}
