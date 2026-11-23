package fyi.fyw.etop2p

import fyi.fyw.etop2p.client.Filter
import fyi.fyw.etop2p.client.Listing
import fyi.fyw.etop2p.client.Texts
import fyi.fyw.etop2p.client.Tri
import fyi.fyw.etop2p.data.LinkMeta
import fyi.fyw.etop2p.data.P2PEntry
import fyi.fyw.etop2p.data.P2PKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ListingTest {

    private val texts = object : Texts {
        override fun typeName(type: Int) = listOf("ME Tunnel", "Fluid Tunnel")[type]

        override fun attachedName(entry: P2PEntry) = if (entry.attachedItem == 7) "ME Interface" else ""

        // Stands in for pinyin: "zw" finds 主网.
        override fun matches(text: String, typed: String) = typed in text || (typed == "zw" && "主网" in text)

        override fun distance(entry: P2PEntry) = entry.key.x.toDouble()
    }

    private fun tunnel(
        x: Int, frequency: Long, output: Boolean, type: Int = 0, name: String = "", label: String = "",
        online: Boolean = true, attached: Int = -1,
    ) = P2PEntry(P2PKey(0, x, 64, 0, 1), type, name, label, frequency, output, online, 0, attached, 0)

    private val main = tunnel(1, 0xA1L, false, name = "主网")
    private val out1 = tunnel(2, 0xA1L, true, name = "主网", label = "chem 1", attached = 7)
    private val out2 = tunnel(3, 0xA1L, true, name = "主网", online = false)
    private val coolant = tunnel(4, 0xB2L, false, type = 1, name = "coolant")
    private val loose = tunnel(5, 0L, false)
    private val all = listOf(main, out1, out2, coolant, loose)

    private fun shown(filter: Filter, links: Map<Long, LinkMeta> = emptyMap()) =
        Listing.filter(all, links, filter, texts).map { it.key.x }

    @Test
    fun `typed words must all be found, in any of a tunnel's texts`() {
        assertThat(shown(Filter(text = "interface"))).containsExactly(2)
        assertThat(shown(Filter(text = "zw chem"))).containsExactly(2)
        assertThat(shown(Filter(text = "4,64,0"))).containsExactly(4)
        assertThat(shown(Filter(text = "00b2"))).containsExactly(4)
    }

    @Test
    fun `tags narrow like the buttons`() {
        assertThat(shown(Filter(text = "@out"))).containsExactly(2, 3)
        assertThat(shown(Filter(text = "@in @b"))).containsExactly(1, 4)
        assertThat(shown(Filter(text = "@u"))).containsExactly(5)
        assertThat(shown(Filter(text = "@type=fluid"))).containsExactly(4)
        assertThat(shown(Filter(output = Tri.YES, online = Tri.NO))).containsExactly(3)
        assertThat(shown(Filter(types = setOf(1)))).containsExactly(4)
    }

    @Test
    fun `favourites are the connections marked so`() {
        assertThat(shown(Filter(favorites = true), mapOf(0xB2L to LinkMeta(favorite = true)))).containsExactly(4)
    }

    @Test
    fun `a connection is listed whole when one of its tunnels is shown`() {
        val groups = Listing.groups(listOf(out2), all, emptyMap(), whole = true)
        assertThat(groups).hasSize(1)
        assertThat(groups[0].input).isEqualTo(main)
        assertThat(groups[0].outputs).containsExactly(out1, out2)
        assertThat(groups[0].name).isEqualTo("主网")
    }

    @Test
    fun `favourite connections come first and the unbound ones last`() {
        val groups = Listing.groups(all, all, mapOf(0xB2L to LinkMeta(favorite = true)), whole = true)
        assertThat(groups.map { it.frequency }).containsExactly(0xB2L, 0xA1L, 0L)
        assertThat(groups.last().outputs).containsExactly(loose)
    }

    @Test
    fun `one pattern names several things with a running number`() {
        assertThat(Listing.numbered("chem", 1)).containsExactly("chem")
        assertThat(Listing.numbered("chem", 2)).containsExactly("chem 1", "chem 2")
        assertThat(Listing.numbered("line {n} out", 2, 5)).containsExactly("line 5 out", "line 6 out")
    }
}
