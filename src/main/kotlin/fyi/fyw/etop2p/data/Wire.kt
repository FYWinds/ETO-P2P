package fyi.fyw.etop2p.data

import cpw.mods.fml.common.network.ByteBufUtils
import io.netty.buffer.ByteBuf

/** The model on the wire. */
internal object Wire {

    fun writeKey(buf: ByteBuf, key: P2PKey) {
        buf.writeInt(key.dim).writeInt(key.x).writeInt(key.y).writeInt(key.z).writeByte(key.side)
    }

    fun readKey(buf: ByteBuf) = P2PKey(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readByte().toInt())

    fun writeKeys(buf: ByteBuf, keys: Collection<P2PKey>) {
        ByteBufUtils.writeVarInt(buf, keys.size, 5)
        keys.forEach { writeKey(buf, it) }
    }

    fun readKeys(buf: ByteBuf): List<P2PKey> = List(ByteBufUtils.readVarInt(buf, 5)) { readKey(buf) }

    fun writeEntry(buf: ByteBuf, entry: P2PEntry) {
        writeKey(buf, entry.key)
        buf.writeByte(entry.type)
        ByteBufUtils.writeUTF8String(buf, entry.name)
        ByteBufUtils.writeUTF8String(buf, entry.label)
        buf.writeLong(entry.frequency)
        buf.writeByte((if (entry.output) 1 else 0) or (if (entry.online) 2 else 0))
        buf.writeShort(entry.channels)
        buf.writeInt(entry.attachedItem)
        buf.writeShort(entry.attachedMeta)
    }

    fun readEntry(buf: ByteBuf): P2PEntry {
        val key = readKey(buf)
        val type = buf.readByte().toInt()
        val name = ByteBufUtils.readUTF8String(buf)
        val label = ByteBufUtils.readUTF8String(buf)
        val frequency = buf.readLong()
        val flags = buf.readByte().toInt()
        return P2PEntry(
            key, type, name, label, frequency, flags and 1 != 0, flags and 2 != 0,
            buf.readShort().toInt(), buf.readInt(), buf.readShort().toInt(),
        )
    }

    fun writeMeta(buf: ByteBuf, meta: LinkMeta) {
        buf.writeByte(meta.color)
        buf.writeBoolean(meta.favorite)
        ByteBufUtils.writeUTF8String(buf, meta.note)
    }

    fun readMeta(buf: ByteBuf) = LinkMeta(buf.readByte().toInt(), buf.readBoolean(), ByteBufUtils.readUTF8String(buf))

    fun writeLinks(buf: ByteBuf, links: Map<Long, LinkMeta>) {
        ByteBufUtils.writeVarInt(buf, links.size, 5)
        links.forEach { (frequency, meta) ->
            buf.writeLong(frequency)
            writeMeta(buf, meta)
        }
    }

    fun readLinks(buf: ByteBuf): Map<Long, LinkMeta> {
        val links = HashMap<Long, LinkMeta>()
        repeat(ByteBufUtils.readVarInt(buf, 5)) { links[buf.readLong()] = readMeta(buf) }
        return links
    }

    fun writeNotice(buf: ByteBuf, notice: Notice) {
        ByteBufUtils.writeUTF8String(buf, notice.key)
        buf.writeByte(notice.args.size)
        notice.args.forEach { ByteBufUtils.writeUTF8String(buf, it) }
        buf.writeBoolean(notice.error)
    }

    fun readNotice(buf: ByteBuf): Notice {
        val key = ByteBufUtils.readUTF8String(buf)
        val args = List(buf.readByte().toInt()) { ByteBufUtils.readUTF8String(buf) }
        return Notice(key, args, buf.readBoolean())
    }

    private fun writeNamed(buf: ByteBuf, pairs: List<Pair<P2PKey, String>>) {
        ByteBufUtils.writeVarInt(buf, pairs.size, 5)
        pairs.forEach { (key, text) ->
            writeKey(buf, key)
            ByteBufUtils.writeUTF8String(buf, text)
        }
    }

    private fun readNamed(buf: ByteBuf): List<Pair<P2PKey, String>> =
        List(ByteBufUtils.readVarInt(buf, 5)) { readKey(buf) to ByteBufUtils.readUTF8String(buf) }

    fun writeOp(buf: ByteBuf, op: Op) {
        when (op) {
            is Op.Bind -> {
                buf.writeByte(0)
                writeKey(buf, op.input)
                writeKeys(buf, op.outputs)
            }
            is Op.MakeInput -> {
                buf.writeByte(1)
                writeKey(buf, op.key)
            }
            is Op.Unbind -> {
                buf.writeByte(2)
                writeKeys(buf, op.keys)
            }
            is Op.Rename -> {
                buf.writeByte(3)
                writeNamed(buf, op.names)
            }
            is Op.Label -> {
                buf.writeByte(4)
                writeNamed(buf, op.labels)
            }
            is Op.Convert -> {
                buf.writeByte(5)
                writeKeys(buf, op.keys)
                buf.writeByte(op.type)
            }
            is Op.Meta -> {
                buf.writeByte(6)
                ByteBufUtils.writeVarInt(buf, op.frequencies.size, 5)
                op.frequencies.forEach { buf.writeLong(it) }
                buf.writeByte(op.color ?: -1)
                buf.writeByte(op.favorite?.let { if (it) 1 else 0 } ?: -1)
                buf.writeBoolean(op.note != null)
                op.note?.let { ByteBufUtils.writeUTF8String(buf, it) }
            }
            Op.Undo -> buf.writeByte(7)
            Op.Close -> buf.writeByte(8)
        }
    }

    fun readOp(buf: ByteBuf): Op = when (val id = buf.readByte().toInt()) {
        0 -> Op.Bind(readKey(buf), readKeys(buf))
        1 -> Op.MakeInput(readKey(buf))
        2 -> Op.Unbind(readKeys(buf))
        3 -> Op.Rename(readNamed(buf))
        4 -> Op.Label(readNamed(buf))
        5 -> Op.Convert(readKeys(buf), buf.readByte().toInt())
        6 -> {
            val frequencies = List(ByteBufUtils.readVarInt(buf, 5)) { buf.readLong() }
            val color = buf.readByte().toInt().takeIf { it >= 0 }
            val favorite = buf.readByte().toInt().takeIf { it >= 0 }?.let { it == 1 }
            Op.Meta(frequencies, color, favorite, if (buf.readBoolean()) ByteBufUtils.readUTF8String(buf) else null)
        }
        7 -> Op.Undo
        8 -> Op.Close
        else -> throw IllegalArgumentException("unknown operation $id")
    }
}
