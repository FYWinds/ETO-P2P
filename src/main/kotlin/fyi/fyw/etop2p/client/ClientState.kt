package fyi.fyw.etop2p.client

import com.cleanroommc.modularui.factory.ClientGUI
import fyi.fyw.etop2p.data.LinkMeta
import fyi.fyw.etop2p.data.Notice
import fyi.fyw.etop2p.data.Op
import fyi.fyw.etop2p.data.P2PEntry
import fyi.fyw.etop2p.data.P2PKey
import fyi.fyw.etop2p.net.Net

/** The network the screen shows, as the server last described it. */
object ClientState : Net.ClientSink {

    val entries = LinkedHashMap<P2PKey, P2PEntry>()
    var links: Map<Long, LinkMeta> = emptyMap()
        private set
    var undo = 0
        private set

    /** The tunnel the card was used on, for the screen to select as it opens. */
    var focus: P2PKey? = null

    /** The last answer to an operation and when it came (system milliseconds), for the status line. */
    var notice: Notice? = null
        private set
    var noticeAt = 0L
        private set

    /** Goes up with every message, so the screen knows when to lay its list out again. */
    var revision = 0
        private set

    /** False once the server said the network is gone. */
    var live = false
        private set

    override fun open(message: Net.Open) {
        entries.clear()
        message.entries.forEach { entries[it.key] = it }
        links = message.links
        undo = 0
        focus = message.focus
        notice = null
        live = true
        revision++
        ClientGUI.open(P2PScreen.create())
    }

    override fun update(message: Net.Update) {
        message.removed.forEach(entries::remove)
        message.changed.forEach { entries[it.key] = it }
        message.links?.let { links = it }
        undo = message.undo
        message.notice?.let {
            notice = it
            noticeAt = System.currentTimeMillis()
        }
        if (message.closed) live = false
        revision++
    }

    fun send(op: Op) {
        Net.channel.sendToServer(Net.Request(op))
    }
}
