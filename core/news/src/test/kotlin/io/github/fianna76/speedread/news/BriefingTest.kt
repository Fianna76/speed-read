package io.github.fianna76.speedread.news

import org.junit.Assert.assertEquals
import org.junit.Test

class BriefingTest {

    private fun item(source: String, title: String) = FeedItem(
        sourceId = source,
        title = title,
        url = "https://$source.example/${title.hashCode()}",
        summary = "",
        imageUrl = null,
        publishedAt = null,
        author = null,
    )

    private val regions = mapOf(
        "rte" to NewsRegion.IRELAND,
        "irishtimes" to NewsRegion.IRELAND,
        "dw" to NewsRegion.EUROPE,
        "bbc" to NewsRegion.WORLD,
        "npr" to NewsRegion.WORLD,
    )

    private val irish1 = item("rte", "Cork hurlers name team for All-Ireland final")
    private val irish2 = item("irishtimes", "House prices rise again in Dublin")
    private val irish3 = item("rte", "Taoiseach announces general election for November")
    private val irish3b = item("irishtimes", "Taoiseach to announce November general election")
    private val europe1 = item("dw", "German coalition agrees budget deal")
    private val world1 = item("bbc", "Hurricane Trudy makes its way towards Mexico")
    private val world1b = item("npr", "Mexico braces as Hurricane Trudy nears coast")
    private val world2 = item("bbc", "Markets fall in Asia")
    private val world3 = item("npr", "Nasa launches Moon mission")

    private val headlines = listOf(irish1, irish2, europe1, world2, world3, irish3, irish3b, world1, world1b)

    @Test
    fun `takes the most covered stories of each region, home news first`() {
        val briefing = Briefing.select(headlines, regions::get)
        assertEquals(listOf(irish3, irish1, europe1, world1, world2), briefing.map { it.lead })
        assertEquals(listOf(irish3b), briefing[0].related)
    }

    @Test
    fun `skips stories already read, in any version`() {
        val briefing = Briefing.select(headlines, regions::get, isRead = { it == irish3b || it == europe1 })
        assertEquals(listOf(irish1, irish2, world1, world2), briefing.map { it.lead })
    }

    @Test
    fun `regions without sources are left out`() {
        val briefing = Briefing.select(listOf(world2, world3), regions::get, perRegion = 1)
        assertEquals(listOf(world2), briefing.map { it.lead })
    }
}
