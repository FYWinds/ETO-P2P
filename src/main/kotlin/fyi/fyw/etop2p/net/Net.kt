package fyi.fyw.etop2p.net

import cpw.mods.fml.common.network.ByteBufUtils
import cpw.mods.fml.common.network.NetworkRegistry
import cpw.mods.fml.common.network.simpleimpl.IMessage
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler
import cpw.mods.fml.common.network.simpleimpl.MessageContext
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper
import cpw.mods.fml.relauncher.Side
import fyi.fyw.etop2p.EtoP2P
import fyi.fyw.etop2p.data.LinkMeta
import fyi.fyw.etop2p.data.Notice
import fyi.fyw.etop2p.data.Op
import fyi.fyw.etop2p.data.P2PEntry
import fyi.fyw.etop2p.data.P2PKey
import fyi.fyw.etop2p.data.Wire
import fyi.fyw.etop2p.server.Sessions
import io.netty.buffer.ByteBuf

/** The mod's channel: the server opens and updates the screen, the client sends operations. */
object Net {
    val channel: SimpleNetworkWrapper = NetworkRegistry.INSTANCE.newSimpleChannel(EtoP2P.MOD_ID)

    /** What the client does with the server's messages; set by the client's setup, absent on a server. */
    var client: ClientSink? = null

    interface ClientSink {
        fun open(message: Open)

        fun update(message: Update)
    }

    fun register() {
        channel.registerMessage(OpenHandler::class.java, Open::class.java, 0, Side.CLIENT)
        channel.registerMessage(UpdateHandler::class.java, Update::class.java, 1, Side.CLIENT)
        channel.registerMessage(OpHandler::class.java, Request::class.java, 2, Side.SERVER)
    }

    /** The network's tunnels, for a screen that opens now; [focus] is the tunnel the card was used on. */
    class Open(
        var entries: List<P2PEntry> = emptyList(),
        var links: Map<Long, LinkMeta> = emptyMap(),
        var focus: P2PKey? = null,
    ) : IMessage {
        override fun toBytes(buf: ByteBuf) {
            ByteBufUtils.writeVarInt(buf, entries.size, 5)
            entries.forEach { Wire.writeEntry(buf, it) }
            Wire.writeLinks(buf, links)
            buf.writeBoolean(focus != null)
            focus?.let { Wire.writeKey(buf, it) }
        }

        override fun fromBytes(buf: ByteBuf) {
            entries = List(ByteBufUtils.readVarInt(buf, 5)) { Wire.readEntry(buf) }
            links = Wire.readLinks(buf)
            focus = if (buf.readBoolean()) Wire.readKey(buf) else null
        }
    }

    /**
     * What changed since the last message: tunnels that are new or different, tunnels that are gone, the
     * connections' tags when any of them changed (all of them, else null), how many steps can be undone, and a line
     * for the player when this answers an operation. [closed] ends the screen's network: it is gone.
     */
    class Update(
        var changed: List<P2PEntry> = emptyList(),
        var removed: List<P2PKey> = emptyList(),
        var links: Map<Long, LinkMeta>? = null,
        var undo: Int = 0,
        var notice: Notice? = null,
        var closed: Boolean = false,
    ) : IMessage {
        override fun toBytes(buf: ByteBuf) {
            ByteBufUtils.writeVarInt(buf, changed.size, 5)
            changed.forEach { Wire.writeEntry(buf, it) }
            Wire.writeKeys(buf, removed)
            buf.writeBoolean(links != null)
            links?.let { Wire.writeLinks(buf, it) }
            buf.writeByte(undo)
            buf.writeBoolean(notice != null)
            notice?.let { Wire.writeNotice(buf, it) }
            buf.writeBoolean(closed)
        }

        override fun fromBytes(buf: ByteBuf) {
            changed = List(ByteBufUtils.readVarInt(buf, 5)) { Wire.readEntry(buf) }
            removed = Wire.readKeys(buf)
            links = if (buf.readBoolean()) Wire.readLinks(buf) else null
            undo = buf.readByte().toInt()
            notice = if (buf.readBoolean()) Wire.readNotice(buf) else null
            closed = buf.readBoolean()
        }
    }

    class Request(var op: Op = Op.Close) : IMessage {
        override fun toBytes(buf: ByteBuf) = Wire.writeOp(buf, op)

        override fun fromBytes(buf: ByteBuf) {
            op = Wire.readOp(buf)
        }
    }

    class OpenHandler : IMessageHandler<Open, IMessage?> {
        override fun onMessage(message: Open, ctx: MessageContext): IMessage? {
            client?.open(message)
            return null
        }
    }

    class UpdateHandler : IMessageHandler<Update, IMessage?> {
        override fun onMessage(message: Update, ctx: MessageContext): IMessage? {
            client?.update(message)
            return null
        }
    }

    class OpHandler : IMessageHandler<Request, IMessage?> {
        override fun onMessage(message: Request, ctx: MessageContext): IMessage? {
            Sessions.handle(ctx.serverHandler.playerEntity, message.op)
            return null
        }
    }
}
