package io.github.fianna76.speedread.news

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FeedParserTest {

    private val source = NewsSource(id = "test", name = "Test News", feedUrl = "https://news.example/rss.xml", homepage = "https://news.example")

    @Test
    fun `parses RSS 2 items with media thumbnails, html summaries and zone-name dates`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/" xmlns:dc="http://purl.org/dc/elements/1.1/">
              <channel><title>Test</title>
                <item>
                  <title><![CDATA[Storm &amp; flood warning]]></title>
                  <link>https://news.example/world/storm?at_medium=RSS&amp;at_campaign=rss</link>
                  <description>&lt;p&gt;Rain is &lt;b&gt;coming&lt;/b&gt;.&lt;/p&gt; &lt;a href="x"&gt;Continue reading...&lt;/a&gt;</description>
                  <pubDate>Sat, 17 Oct 2026 08:46:51 EDT</pubDate>
                  <dc:creator>Ada Writer</dc:creator>
                  <media:thumbnail width="240" url="https://img.example/small.jpg"/>
                  <media:content medium="image" width="1024" url="https://img.example/large.jpg"/>
                </item>
                <item>
                  <title>Second</title>
                  <link>https://news.example/second</link>
                  <description><![CDATA[<img src='/pics/second.jpg'/><p>Teaser.</p>]]></description>
                  <pubDate>Sat, 17 Oct 2026 13:59:00 GMT</pubDate>
                </item>
              </channel>
            </rss>"""

        val items = FeedParser.parse(xml, source)

        assertEquals(2, items.size)
        with(items[0]) {
            assertEquals("test", sourceId)
            assertEquals("Storm & flood warning", title)
            assertEquals("https://news.example/world/storm?at_medium=RSS&at_campaign=rss", url)
            assertEquals("https://news.example/world/storm", canonicalUrl)
            assertEquals("Rain is coming.", summary)
            assertEquals("https://img.example/large.jpg", imageUrl)
            assertEquals(Instant.parse("2026-10-17T12:46:51Z").toEpochMilli(), publishedAt)
            assertEquals("Ada Writer", author)
        }
        with(items[1]) {
            assertEquals("https://news.example/pics/second.jpg", imageUrl)
            assertEquals("Teaser.", summary)
            assertEquals(Instant.parse("2026-10-17T13:59:00Z").toEpochMilli(), publishedAt)
            assertNull(author)
        }
    }

    @Test
    fun `parses Atom entries`() {
        val xml = """<?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Atom</title>
              <entry>
                <title type="html">Atom &lt;em&gt;story&lt;/em&gt;</title>
                <link rel="enclosure" href="https://news.example/audio.mp3"/>
                <link href="https://news.example/atom-story"/>
                <summary>Short summary</summary>
                <published>2026-10-16T09:30:00+02:00</published>
                <author><name>Grace Reporter</name></author>
              </entry>
            </feed>"""

        val item = FeedParser.parse(xml, source).single()

        assertEquals("Atom story", item.title)
        assertEquals("https://news.example/atom-story", item.url)
        assertEquals("Short summary", item.summary)
        assertEquals(Instant.parse("2026-10-16T07:30:00Z").toEpochMilli(), item.publishedAt)
        assertEquals("Grace Reporter", item.author)
    }

    @Test
    fun `skips video, live and gallery pages, duplicates and items without links`() {
        val links = listOf(
            "https://news.example/news/videos/abc",
            "https://news.example/news/live/world-123",
            "https://news.example/gallery/2026/photos",
            "https://news.example/story?cmp=rss",
            "https://news.example/story",
            "",
            "https://news.example/livestock-prices",
        )
        val xml = "<rss><channel>" + links.joinToString("") { "<item><title>T</title><link>$it</link></item>" } + "</channel></rss>"

        val items = FeedParser.parse(xml, source)

        assertEquals(listOf("https://news.example/story?cmp=rss", "https://news.example/livestock-prices"), items.map { it.url })
    }

    @Test
    fun `long summaries are cut at a word boundary`() {
        val long = List(200) { "word$it" }.joinToString(" ")
        val xml = "<rss><channel><item><title>T</title><link>https://news.example/a</link><description>$long</description></item></channel></rss>"

        val summary = FeedParser.parse(xml, source).single().summary

        assertTrue(summary.length <= FeedParser.MAX_SUMMARY_CHARS)
        assertTrue(summary.endsWith("\u2026"))
        assertTrue(long.startsWith(summary.removeSuffix("\u2026")))
    }

    @Test
    fun `respects the item limit`() {
        val xml = "<rss><channel>" + (1..30).joinToString("") { "<item><title>T$it</title><link>https://news.example/$it</link></item>" } +
            "</channel></rss>"

        assertEquals(10, FeedParser.parse(xml, source, maxItems = 10).size)
    }

    @Test(expected = FeedException::class)
    fun `html pages are rejected`() {
        FeedParser.parse("<html><body><p>Not a feed</p></body></html>", source)
    }

    @Test
    fun `an empty feed has no items`() {
        assertEquals(emptyList<FeedItem>(), FeedParser.parse("<rss><channel><title>Quiet day</title></channel></rss>", source))
    }

    @Test
    fun `unparseable dates are ignored`() {
        assertNull(FeedParser.parseDate("yesterday"))
    }
}
