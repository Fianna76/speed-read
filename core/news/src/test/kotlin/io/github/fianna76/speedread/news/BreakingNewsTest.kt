package io.github.fianna76.speedread.news

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BreakingNewsTest {

    private val now = 1_800_000_000_000L
    private val hour = 60 * 60 * 1000L

    private fun item(source: String, title: String, ageHours: Double? = 0.5) = FeedItem(
        sourceId = source,
        title = title,
        url = "https://$source.example/${title.hashCode()}",
        summary = "",
        imageUrl = null,
        publishedAt = ageHours?.let { now - (it * hour).toLong() },
        author = null,
    )

    private val rte = item("rte", "Taoiseach announces general election for November")
    private val times = item("irishtimes", "Taoiseach to announce November general election")

    @Test
    fun `a lead story at two outlets is breaking`() {
        val feeds = mapOf(
            "rte" to listOf(rte, item("rte", "Cork hurlers name team for final")),
            "irishtimes" to listOf(item("irishtimes", "House prices rise again in Dublin"), times),
        )
        val found = BreakingNews.find(feeds, now)
        assertEquals(1, found.size)
        assertEquals(setOf(rte, times), (listOf(found[0].lead) + found[0].related).toSet())
    }

    @Test
    fun `a story at one outlet is not breaking unless it is labelled so`() {
        val single = mapOf("rte" to listOf(rte), "irishtimes" to listOf(item("irishtimes", "House prices rise again")))
        assertTrue(BreakingNews.find(single, now).isEmpty())

        val labelled = item("rte", "Breaking: Major fire at Dublin port warehouse")
        assertEquals(labelled, BreakingNews.find(mapOf("rte" to listOf(labelled)), now).single().lead)
    }

    @Test
    fun `stories below the top of the feed are ignored`() {
        val filler = (1..BreakingNews.TOP_PER_FEED).map { item("irishtimes", "Unrelated story number $it about topic$it") }
        val feeds = mapOf("rte" to listOf(rte), "irishtimes" to filler + times)
        assertTrue(BreakingNews.find(feeds, now).isEmpty())
    }

    @Test
    fun `old, read and already alerted stories are skipped`() {
        val old = mapOf(
            "rte" to listOf(item("rte", rte.title, ageHours = 5.0)),
            "irishtimes" to listOf(item("irishtimes", times.title, ageHours = 4.0)),
        )
        assertTrue(BreakingNews.find(old, now).isEmpty())

        val feeds = mapOf("rte" to listOf(rte), "irishtimes" to listOf(times))
        assertTrue(BreakingNews.find(feeds, now, isRead = { it == times }).isEmpty())
        assertTrue(BreakingNews.find(feeds, now, alerted = listOf(AlertedStory(rte.canonicalUrl, rte.title))).isEmpty())
        // Another outlet's version of a story already alerted does not alert again.
        val other = AlertedStory("https://journal.example/x", "General election in November, Taoiseach announces")
        assertTrue(BreakingNews.find(feeds, now, alerted = listOf(other)).isEmpty())
    }

    @Test
    fun `undated stories count as fresh`() {
        val feeds = mapOf(
            "rte" to listOf(item("rte", rte.title, ageHours = null)),
            "irishtimes" to listOf(item("irishtimes", times.title, ageHours = null)),
        )
        assertEquals(1, BreakingNews.find(feeds, now).size)
    }

    @Test
    fun `breaking labels`() {
        assertTrue(BreakingNews.isLabelledBreaking("BREAKING: Storm warning upgraded"))
        assertTrue(BreakingNews.isLabelledBreaking("Breaking news | Garda appeal"))
        assertTrue(BreakingNews.isLabelledBreaking("Just in - Ryanair cancels flights"))
        assertFalse(BreakingNews.isLabelledBreaking("Breaking Bad star visits Dublin"))
        assertFalse(BreakingNews.isLabelledBreaking("Record breaking heat in Spain"))
    }
}
