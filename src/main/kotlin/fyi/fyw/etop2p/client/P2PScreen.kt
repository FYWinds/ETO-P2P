package fyi.fyw.etop2p.client

import com.cleanroommc.modularui.api.drawable.IKey
import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.screen.ModularScreen
import fyi.fyw.etop2p.EtoP2P
import fyi.fyw.etop2p.data.Op

/** The card's screen. */
class P2PScreen private constructor(panel: ModularPanel) : ModularScreen(EtoP2P.MOD_ID, panel) {

    override fun onClose() {
        super.onClose()
        ClientState.send(Op.Close)
    }

    companion object {
        fun create(): P2PScreen {
            val panel = ModularPanel.defaultPanel("etop2p_main", 320, 200)
            panel.child(IKey.dynamic { "P2P: ${ClientState.entries.size}" }.asWidget().pos(8, 8))
            return P2PScreen(panel)
        }
    }
}
