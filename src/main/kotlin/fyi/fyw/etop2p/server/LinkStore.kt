package fyi.fyw.etop2p.server

import fyi.fyw.etop2p.data.LinkMeta
import fyi.fyw.etop2p.data.P2PKey
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraft.world.WorldSavedData
import net.minecraftforge.common.DimensionManager
import net.minecraftforge.common.util.Constants

/**
 * What the card keeps besides AE2's own data, saved with the world: a connection's colour tag, favourite mark and
 * note by its frequency, a tunnel's label by where it is. Nothing is dropped when a frequency or tunnel goes away:
 * an entry is a few bytes and the tunnel may only be unloaded.
 */
class LinkStore(name: String) : WorldSavedData(name) {

    private val links = HashMap<Long, LinkMeta>()
    private val labels = HashMap<P2PKey, String>()

    fun link(frequency: Long): LinkMeta = links[frequency] ?: EMPTY

    fun setLink(frequency: Long, meta: LinkMeta) {
        if (frequency == 0L || link(frequency) == meta) return
        if (meta.isEmpty) links.remove(frequency) else links[frequency] = meta
        markDirty()
    }

    fun label(key: P2PKey): String = labels[key] ?: ""

    fun setLabel(key: P2PKey, label: String) {
        if (label(key) == label) return
        if (label.isEmpty()) labels.remove(key) else labels[key] = label
        markDirty()
    }

    override fun readFromNBT(tag: NBTTagCompound) {
        links.clear()
        labels.clear()
        val linkList = tag.getTagList("links", Constants.NBT.TAG_COMPOUND)
        for (i in 0 until linkList.tagCount()) {
            val entry = linkList.getCompoundTagAt(i)
            links[entry.getLong("freq")] =
                LinkMeta(entry.getByte("color").toInt(), entry.getBoolean("favorite"), entry.getString("note"))
        }
        val labelList = tag.getTagList("labels", Constants.NBT.TAG_COMPOUND)
        for (i in 0 until labelList.tagCount()) {
            val entry = labelList.getCompoundTagAt(i)
            val key = P2PKey(
                entry.getInteger("dim"), entry.getInteger("x"), entry.getInteger("y"), entry.getInteger("z"),
                entry.getByte("side").toInt(),
            )
            labels[key] = entry.getString("label")
        }
    }

    override fun writeToNBT(tag: NBTTagCompound) {
        val linkList = NBTTagList()
        links.forEach { (frequency, meta) ->
            linkList.appendTag(
                NBTTagCompound().apply {
                    setLong("freq", frequency)
                    setByte("color", meta.color.toByte())
                    setBoolean("favorite", meta.favorite)
                    setString("note", meta.note)
                },
            )
        }
        tag.setTag("links", linkList)
        val labelList = NBTTagList()
        labels.forEach { (key, label) ->
            labelList.appendTag(
                NBTTagCompound().apply {
                    setInteger("dim", key.dim)
                    setInteger("x", key.x)
                    setInteger("y", key.y)
                    setInteger("z", key.z)
                    setByte("side", key.side.toByte())
                    setString("label", label)
                },
            )
        }
        tag.setTag("labels", labelList)
    }

    companion object {
        private const val NAME = "etop2p"
        private val EMPTY = LinkMeta()

        /** The one store of the save, kept with the overworld. */
        fun get(): LinkStore {
            val storage = DimensionManager.getWorld(0).mapStorage
            return storage.loadData(LinkStore::class.java, NAME) as LinkStore?
                ?: LinkStore(NAME).also { storage.setData(NAME, it) }
        }
    }
}
