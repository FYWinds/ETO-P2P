package fyi.fyw.etop2p.data

import appeng.api.AEApi
import appeng.api.definitions.IItemDefinition
import cpw.mods.fml.common.registry.GameRegistry
import net.minecraft.item.ItemStack

/**
 * The tunnel types the card knows, in a fixed order: a type's index is what goes over the wire. A type whose item
 * this pack does not have stays in the list without a stack.
 */
object P2PTypes {

    class Type(val index: Int, val id: String, val stack: ItemStack?)

    val all: List<Type> by lazy {
        val parts = AEApi.instance().definitions().parts()
        fun of(definition: IItemDefinition): ItemStack? = definition.maybeStack(1).orNull()
        listOf(
            "me" to of(parts.p2PTunnelME()),
            "eu" to of(parts.p2PTunnelEU()),
            "rf" to of(parts.p2PTunnelRF()),
            "redstone" to of(parts.p2PTunnelRedstone()),
            "liquids" to of(parts.p2PTunnelLiquids()),
            "items" to of(parts.p2PTunnelItems()),
            "light" to of(parts.p2PTunnelLight()),
            "opencomputers" to of(parts.p2PTunnelOpenComputers()),
            "pressure" to of(parts.p2PTunnelPneumaticCraft()),
            "gregtech" to of(parts.p2PTunnelGregtech()),
            "interface" to of(parts.p2PTunnelMEInterface()),
            "sound" to of(parts.p2PTunnelSound()),
            "fluid_interface" to GameRegistry.findItem("ae2fc", "part_fluid_p2p_interface")?.let { ItemStack(it) },
        ).mapIndexed { index, (id, stack) -> Type(index, id, stack) }
    }

    /** The types this pack has. */
    val available: List<Type> get() = all.filter { it.stack != null }

    /** The index of the type a tunnel's item is, -1 for a tunnel of another mod. */
    fun of(stack: ItemStack?): Int {
        if (stack == null) return -1
        return all.firstOrNull { it.stack != null && it.stack.item === stack.item && it.stack.itemDamage == stack.itemDamage }
            ?.index ?: -1
    }

    fun stack(type: Int): ItemStack? = all.getOrNull(type)?.stack?.copy()
}
