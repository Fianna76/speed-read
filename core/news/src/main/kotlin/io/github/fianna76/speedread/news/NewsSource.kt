package io.github.fianna76.speedread.news

/**
 * A news outlet the Newsstand reads from: its public RSS feed of top stories, plus hints that
 * help [ArticleExtractor] find the story text on its article pages.
 */
data class NewsSource(
    /** Stable identifier, stored in settings; never rename. */
    val id: String,
    val name: String,
    val feedUrl: String,
    val homepage: String,
    /**
     * CSS selector of the element holding an article's body text on this outlet's pages. Only a
     * hint: extraction falls back to content scoring when it matches nothing useful.
     */
    val bodySelector: String? = null,
)

/**
 * The built-in sources. Every outlet here publishes free RSS feeds of its headlines and serves
 * its articles without a paywall or sign-in, so the app can link to and read them like a browser.
 *
 * Reuters is deliberately missing: it has offered no public feed since 2020 and blocks
 * automated access to its pages. AP likewise has no public feed.
 */
object NewsSources {

    val ALL: List<NewsSource> = listOf(
        NewsSource(
            id = "aljazeera",
            name = "Al Jazeera",
            feedUrl = "https://www.aljazeera.com/xml/rss/all.xml",
            homepage = "https://www.aljazeera.com",
            bodySelector = ".wysiwyg",
        ),
        NewsSource(
            id = "bbc",
            name = "BBC News",
            feedUrl = "https://feeds.bbci.co.uk/news/rss.xml",
            homepage = "https://www.bbc.co.uk/news",
            bodySelector = "article",
        ),
        NewsSource(
            id = "dw",
            name = "DW",
            feedUrl = "https://rss.dw.com/xml/rss-en-top",
            homepage = "https://www.dw.com/en",
            bodySelector = ".rich-text",
        ),
        NewsSource(
            id = "guardian",
            name = "The Guardian",
            feedUrl = "https://www.theguardian.com/international/rss",
            homepage = "https://www.theguardian.com",
            bodySelector = "#maincontent",
        ),
        NewsSource(
            id = "france24",
            name = "France 24",
            feedUrl = "https://www.france24.com/en/rss",
            homepage = "https://www.france24.com/en",
            bodySelector = ".t-content__body",
        ),
        NewsSource(
            id = "npr",
            name = "NPR",
            feedUrl = "https://feeds.npr.org/1001/rss.xml",
            homepage = "https://www.npr.org",
            bodySelector = "#storytext",
        ),
        NewsSource(
            id = "abc_au",
            name = "ABC News (Australia)",
            feedUrl = "https://www.abc.net.au/news/feed/45910/rss.xml",
            homepage = "https://www.abc.net.au/news",
        ),
        NewsSource(
            id = "cbc",
            name = "CBC News",
            feedUrl = "https://www.cbc.ca/webfeed/rss/rss-topstories",
            homepage = "https://www.cbc.ca/news",
            bodySelector = ".story",
        ),
        NewsSource(
            id = "pbs",
            name = "PBS NewsHour",
            feedUrl = "https://www.pbs.org/newshour/feeds/rss/headlines",
            homepage = "https://www.pbs.org/newshour",
            bodySelector = ".body-text",
        ),
    )

    fun byId(id: String): NewsSource? = ALL.firstOrNull { it.id == id }
}
