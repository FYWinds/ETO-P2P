package fyi.fyw.etop2p.client

import fyi.fyw.etolib.text.TextMatch
import fyi.fyw.etop2p.data.P2PEntry
import fyi.fyw.etop2p.data.P2PTypes
import net.minecraft.client.Minecraft
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.StatCollector

/** The list's texts as the running game has them. */
object GameTexts : Texts {

    private val attachedNames = HashMap<Long, String>()

    /** AE2 calls each "P2P Tunnel - ME"; on a screen of nothing but tunnels only what follows the dash tells them apart. */
    override fun typeName(type: Int): String =
        P2PTypes.all.getOrNull(type)?.stack?.displayName?.substringAfter(" - ") ?: StatCollector.translateToLocal("etop2p.type.other")

    fun typeStack(type: Int): ItemStack? = P2PTypes.all.getOrNull(type)?.stack

    /** The item of what the tunnel faces, as the server named it by id and damage. */
    fun attachedStack(entry: P2PEntry): ItemStack? {
        if (entry.attachedItem < 0) return null
        val item = Item.getItemById(entry.attachedItem) ?: return null
        return ItemStack(item, 1, entry.attachedMeta)
    }

    override fun attachedName(entry: P2PEntry): String {
        if (entry.attachedItem < 0) return ""
        return attachedNames.getOrPut(entry.attachedItem.toLong() shl 32 or entry.attachedMeta.toLong()) {
            // An item of another mod may throw for a damage value it does not expect.
            runCatching { attachedStack(entry)?.displayName }.getOrNull() ?: ""
        }
    }

    override fun matches(text: String, typed: String): Boolean = TextMatch.contains(text, typed)

    /** In blocks; a tunnel of another dimension is farther than any of this one. */
    override fun distance(entry: P2PEntry): Double {
        val player = Minecraft.getMinecraft().thePlayer ?: return Double.MAX_VALUE
        if (player.worldObj.provider.dimensionId != entry.key.dim) return Double.MAX_VALUE
        return player.getDistance(entry.key.x + 0.5, entry.key.y + 0.5, entry.key.z + 0.5)
    }
}
