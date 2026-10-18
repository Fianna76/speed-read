package io.github.fianna76.speedread.news

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedDiscoveryTest {

    @Test
    fun `typed addresses are normalised`() {
        assertEquals("https://example.com/news", FeedDiscovery.normalize(" example.com/news "))
        assertEquals("http://blog.example/feed", FeedDiscovery.normalize("http://blog.example/feed"))
        assertNull(FeedDiscovery.normalize("not a url"))
        assertNull(FeedDiscovery.normalize("localhost"))
        assertNull(FeedDiscovery.normalize("ftp://example.com/feed"))
    }

    @Test
    fun `pages advertise their feeds`() {
        val html = """
            <html><head>
            <link rel="stylesheet" href="/style.css">
            <link rel="alternate" type="application/rss+xml" title="News" href="/news/feed/">
            <link rel="Alternate" type="application/atom+xml" href="https://example.com/atom.xml">
            <link rel="alternate" type="application/json" href="/api">
            <link rel="alternate" hreflang="ga" href="/ga/">
            </head><body></body></html>
        """.trimIndent()
        assertEquals(
            listOf("https://example.com/news/feed/", "https://example.com/atom.xml"),
            FeedDiscovery.advertisedFeeds(html, "https://example.com/"),
        )
    }

    @Test
    fun `feeds are recognised and named`() {
        val rss = "<?xml version=\"1.0\"?><rss><channel><title> The Ditch </title><link>https://www.ontheditch.com</link><item/></channel></rss>"
        val atom = "<feed xmlns=\"http://www.w3.org/2005/Atom\"><title>Village blog</title><link rel=\"alternate\" href=\"https://village.example/\"/></feed>"
        assertTrue(FeedDiscovery.isFeed(rss))
        assertTrue(FeedDiscovery.isFeed(atom))
        assertFalse(FeedDiscovery.isFeed("<!doctype html><html><head><title>x</title></head></html>"))
        assertEquals("The Ditch", FeedDiscovery.title(rss))
        assertEquals("Village blog", FeedDiscovery.title(atom))
        assertEquals("https://www.ontheditch.com", FeedDiscovery.homepage(rss, "https://www.ontheditch.com/feed"))
        assertEquals("https://village.example/", FeedDiscovery.homepage(atom, "https://village.example/atom.xml"))
        assertEquals("https://other.example", FeedDiscovery.homepage("<rss><channel/></rss>", "https://other.example/rss"))
    }

    @Test
    fun `custom sources get stable ids and are found by id`() {
        val id = FeedDiscovery.sourceId("https://example.com/feed")
        assertEquals(id, FeedDiscovery.sourceId("https://example.com/feed"))
        assertTrue(id.startsWith(FeedDiscovery.CUSTOM_PREFIX))
        val source = NewsSource(id, "Example", "https://example.com/feed", "https://example.com", NewsRegion.IRELAND)
        try {
            NewsSources.custom = listOf(source)
            assertEquals(source, NewsSources.byId(id))
            assertEquals(source, NewsSources.all.last())
            with(NewsSources) { assertTrue(source.isCustom) }
        } finally {
            NewsSources.custom = emptyList()
        }
    }
}
