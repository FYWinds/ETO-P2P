package fyi.fyw.etop2p

import cpw.mods.fml.common.Mod
import cpw.mods.fml.common.event.FMLInitializationEvent
import cpw.mods.fml.common.event.FMLPreInitializationEvent
import cpw.mods.fml.common.event.FMLServerStartingEvent
import cpw.mods.fml.common.event.FMLServerStoppedEvent
import cpw.mods.fml.common.registry.GameRegistry
import fyi.fyw.etop2p.client.ClientSetup
import fyi.fyw.etop2p.dev.DevCommand
import fyi.fyw.etop2p.item.P2PCardItem
import fyi.fyw.etop2p.net.Net
import fyi.fyw.etop2p.server.Sessions
import net.minecraft.item.ItemStack
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

@Mod(
    modid = EtoP2P.MOD_ID,
    name = "ETO P2P",
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    modLanguageAdapter = "net.shadowfacts.forgelin.KotlinAdapter",
    dependencies = "required-after:forgelin;required-after:appliedenergistics2;required-after:modularui2;" +
        "after:ae2fc;after:betterp2p",
)
object EtoP2P {
    const val MOD_ID = "etop2p"
    val LOG: Logger = LogManager.getLogger(MOD_ID)

    @Mod.EventHandler
    fun preInit(event: FMLPreInitializationEvent) {
        GameRegistry.registerItem(P2PCardItem, "p2p_card")
        Net.register()
        Sessions.register()
        if (event.side.isClient) ClientSetup.register()
    }

    @Mod.EventHandler
    fun init(event: FMLInitializationEvent) {
        // The card and BetterP2P's advanced memory card trade one for one, both ways.
        val theirs = GameRegistry.findItem("betterp2p", "advanced_memory_card") ?: return
        GameRegistry.addShapelessRecipe(ItemStack(P2PCardItem), ItemStack(theirs))
        GameRegistry.addShapelessRecipe(ItemStack(theirs), ItemStack(P2PCardItem))
    }

    @Mod.EventHandler
    fun serverStarting(event: FMLServerStartingEvent) {
        if (DevCommand.enabled) event.registerServerCommand(DevCommand)
    }

    @Mod.EventHandler
    fun serverStopped(event: FMLServerStoppedEvent) = Sessions.clear()
}
