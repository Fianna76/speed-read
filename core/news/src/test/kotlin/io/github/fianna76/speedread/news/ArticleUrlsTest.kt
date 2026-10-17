package io.github.fianna76.speedread.news

import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleUrlsTest {

    @Test
    fun `tracking parameters and fragments are removed`() {
        assertEquals(
            "https://www.bbc.co.uk/news/articles/c1",
            ArticleUrls.canonical("https://www.bbc.co.uk/news/articles/c1?at_medium=RSS&at_campaign=rss#comments"),
        )
        assertEquals(
            "https://www.dw.com/en/story/a-1?page=2",
            ArticleUrls.canonical("https://WWW.DW.com/en/story/a-1?maca=en-rss&page=2&utm_source=x"),
        )
    }

    @Test
    fun `display host drops www`() {
        assertEquals("aljazeera.com", ArticleUrls.displayHost("https://www.aljazeera.com/news/1"))
    }

    @Test
    fun `every built-in source has a unique id and an https feed`() {
        val sources = NewsSources.ALL
        assertEquals(sources.size, sources.map { it.id }.toSet().size)
        sources.forEach { assert(it.feedUrl.startsWith("https://")) { it.id } }
    }
}
