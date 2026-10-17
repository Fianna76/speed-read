package io.github.fianna76.speedread.testing

import io.github.fianna76.speedread.data.news.WebClient
import io.github.fianna76.speedread.news.FeedItem
import io.github.fianna76.speedread.news.NewsSource
import io.github.fianna76.speedread.news.NewsSources
import io.github.fianna76.speedread.ui.news.NewsArticle
import java.io.IOException

/** Builders for news feeds and article pages, plus a scripted [WebClient]. */
object TestNews {

    fun source(id: String, name: String = id.uppercase()) =
        NewsSource(id = id, name = name, feedUrl = "https://$id.example/feed.xml", homepage = "https://$id.example")

    fun item(sourceId: String, slug: String, title: String = "Story $slug", publishedAt: Long? = null) = FeedItem(
        sourceId = sourceId,
        title = title,
        url = "https://$sourceId.example/news/$slug",
        summary = "Summary of $slug",
        imageUrl = null,
        publishedAt = publishedAt,
        author = null,
    )

    /** Headlines as the Newsstand shows them, published [now] minus a few minutes or hours. */
    fun sampleArticles(now: Long): List<NewsArticle> {
        val minute = 60_000L
        fun article(sourceId: String, slug: String, title: String, summary: String, agoMinutes: Long, progress: Float? = null) =
            NewsArticle(
                item = item(sourceId, slug, title, publishedAt = now - agoMinutes * minute).copy(summary = summary),
                source = NewsSources.byId(sourceId)!!,
                progress = progress,
            )
        return listOf(
            article(
                "bbc", "pope", "Pope discharged from hospital days after lung cancer surgery",
                "The pontiff will give the traditional Angelus prayer and blessing in St Peter's Square on Sunday.", 12,
            ),
            article(
                "aljazeera", "tunisia", "Thousands rally for biggest protest in years against Tunisia's Saied",
                "Unions and opposition groups unite to demonstrate against poor living conditions and restrictions on political freedoms.",
                25, progress = 0.4f,
            ),
            article(
                "dw", "gaming", "China's influence in the gaming industry is growing",
                "Home to more than 700 million gamers, China is reshaping the global industry, from studios to streaming platforms.",
                95,
            ),
        )
    }

    /** An RSS feed whose items link to `https://<id>.example/news/<slug>`. */
    fun rss(sourceId: String, vararg slugs: String): String =
        "<rss><channel><title>$sourceId</title>" +
            slugs.joinToString("") { "<item><title>Story $it</title><link>https://$sourceId.example/news/$it</link></item>" } +
            "</channel></rss>"

    /** An article page with [paragraphs] paragraphs of 13 words each. */
    fun articlePage(title: String = "A story", paragraphs: Int = 6): String =
        "<html><head><meta property=\"og:title\" content=\"$title\"></head><body><nav><a href=\"/\">Home</a></nav><article>" +
            (1..paragraphs).joinToString("") { "<p>Paragraph $it explains what happened today, according to officials and several witnesses nearby.</p>" } +
            "</article></body></html>"
}

/** Serves canned responses by URL; unknown URLs fail like an unreachable host. */
class FakeWebClient(responses: Map<String, String> = emptyMap()) : WebClient {

    val responses = responses.toMutableMap()
    val requests = mutableListOf<String>()

    override suspend fun fetchText(url: String): String {
        requests += url
        return responses[url] ?: throw IOException("Unreachable: $url")
    }
}
