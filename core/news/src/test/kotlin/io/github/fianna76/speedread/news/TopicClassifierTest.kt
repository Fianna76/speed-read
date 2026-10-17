package io.github.fianna76.speedread.news

import org.junit.Assert.assertEquals
import org.junit.Test

class TopicClassifierTest {

    private fun topics(url: String, vararg categories: String) =
        TopicClassifier.topics(FeedItem("x", "T", url, "", null, null, null, categories.toList()))

    @Test
    fun `sections in the url path give the topic`() {
        assertEquals(setOf(NewsTopic.SPORT), topics("https://www.rte.ie/sport/rugby/2026/1017/1595743-leinster-rout/"))
        assertEquals(setOf(NewsTopic.BUSINESS), topics("https://www.rte.ie/news/business/2026/1017/1595710-china-cars-ev/"))
        assertEquals(setOf(NewsTopic.CLIMATE), topics("https://english.elpais.com/climate/2026-10-17/medellin-went-green/"))
        assertEquals(setOf(NewsTopic.HEALTH), topics("https://www.cbc.ca/news/health/ebola-congo-children-deaths-9.7384641"))
    }

    @Test
    fun `feed categories add topics`() {
        assertEquals(setOf(NewsTopic.POLITICS), topics("https://www.thejournal.ie/pay-talks-7196/", "Government", "Unions"))
        assertEquals(setOf(NewsTopic.SPORT, NewsTopic.CULTURE), topics("https://example.ie/a/b", "Football", "Music"))
    }

    @Test
    fun `the article slug and unrelated words are ignored`() {
        assertEquals(emptySet<NewsTopic>(), topics("https://www.example.ie/news/ireland/sport-shop-closes-after-50-years/"))
        assertEquals(emptySet<NewsTopic>(), topics("https://kyivindependent.com/russia-attacks/", "War", "Russia's attacks on energy"))
        assertEquals(emptySet<NewsTopic>(), topics("not a url"))
    }
}
