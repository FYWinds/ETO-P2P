package fyi.fyw.etop2p.client.ui

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import com.cleanroommc.modularui.widget.Widget
import fyi.fyw.etolib.ui.Look
import fyi.fyw.etolib.ui.UiText.tr
import fyi.fyw.etolib.ui.fitTo
import fyi.fyw.etolib.ui.guiWidth
import fyi.fyw.etop2p.client.ClientState
import fyi.fyw.etop2p.client.ScreenState

/** How long an answer from the server stays on the line. */
private const val NOTICE_MS = 6000L

/** The line under the list: how many tunnels are shown and picked at the left, the server's last answer at the right. */
internal class StatusLine(private val state: ScreenState) : Widget<StatusLine>() {

    override fun draw(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
        val w = area.w()
        val counts = tr("etop2p.status.counts", state.shown.size, ClientState.entries.size) +
            if (state.selected.isEmpty()) "" else "  " + tr("etop2p.status.picked", state.selected.size)
        var right = w
        val notice = ClientState.notice?.takeIf { System.currentTimeMillis() - ClientState.noticeAt < NOTICE_MS }
        val line = when {
            !ClientState.live -> tr("etop2p.notice.gone") to Look.RED_INK
            notice != null -> tr(notice.key, *notice.args.toTypedArray()) to if (notice.error) Look.RED_INK else Look.OK_INK
            else -> tr("etop2p.status.hint") to Look.HINT
        }
        val text = line.first.fitTo(w - counts.guiWidth - 12)
        Look.textRight(text, right, 1, line.second)
        right -= text.guiWidth + 12
        Look.text(counts.fitTo(right), 0, 1, Look.MUTED)
    }
}
