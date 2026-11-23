package fyi.fyw.etop2p.client

import fyi.fyw.etop2p.net.Net

/** What only a client has: the screen and what feeds it. */
object ClientSetup {
    fun register() {
        Net.client = ClientState
    }
}
