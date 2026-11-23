package fyi.fyw.etop2p.client

import fyi.fyw.etop2p.dev.DevCommand
import fyi.fyw.etop2p.dev.DevInput
import fyi.fyw.etop2p.net.Net
import net.minecraftforge.common.MinecraftForge

/** What only a client has: the screen, what feeds it, and what it points out in the world. */
object ClientSetup {
    fun register() {
        Net.client = ClientState
        MinecraftForge.EVENT_BUS.register(Highlights)
        if (DevCommand.enabled) DevInput.register()
    }
}
