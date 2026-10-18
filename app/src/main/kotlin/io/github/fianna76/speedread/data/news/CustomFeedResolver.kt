package io.github.fianna76.speedread.data.news

import io.github.fianna76.speedread.news.ArticleUrls
import io.github.fianna76.speedread.news.FeedDiscovery
import io.github.fianna76.speedread.news.FeedParser
import io.github.fianna76.speedread.news.NewsRegion
import io.github.fianna76.speedread.news.NewsSource
import io.github.fianna76.speedread.news.NewsSources
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URI

/** Finds the feed behind an address the user typed, for "Add a feed". */
class CustomFeedResolver(private val web: WebClient) {

    sealed interface Result {
        /** A new feed, named after itself. */
        data class Found(val source: NewsSource) : Result

        /** The feed is one of the built-in sources; switch that on instead. */
        data class BuiltIn(val source: NewsSource) : Result

        /** Not a web address. */
        data object Invalid : Result

        /** The site couldn't be reached. */
        data object Network : Result

        /** The page loaded but no feed with stories was found. */
        data object NoFeed : Result
    }

    suspend fun resolve(input: String, region: NewsRegion): Result {
        val url = FeedDiscovery.normalize(input) ?: return Result.Invalid
        val first = try {
            web.fetchText(url)
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            return Result.Network
        }
        if (FeedDiscovery.isFeed(first)) return found(url, first, region) ?: Result.NoFeed
        val origin = URI(url).let { "${it.scheme}://${it.host}" }
        val candidates = (FeedDiscovery.advertisedFeeds(first, url) + FeedDiscovery.COMMON_PATHS.map { origin + it })
            .distinct()
            .take(MAX_CANDIDATES)
        for (candidate in candidates) {
            val text = try {
                web.fetchText(candidate)
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                continue
            }
            if (FeedDiscovery.isFeed(text)) found(candidate, text, region)?.let { return it }
        }
        return Result.NoFeed
    }

    /** A source for the feed at [feedUrl], or `null` if the feed has no usable stories. */
    private suspend fun found(feedUrl: String, xml: String, region: NewsRegion): Result? {
        NewsSources.ALL.firstOrNull { sameFeed(it.feedUrl, feedUrl) }?.let { return Result.BuiltIn(it) }
        val id = FeedDiscovery.sourceId(feedUrl)
        val probe = NewsSource(id, "", feedUrl, feedUrl, region)
        val items = withContext(Dispatchers.Default) { runCatching { FeedParser.parse(xml, probe, maxItems = 1) }.getOrDefault(emptyList()) }
        if (items.isEmpty()) return null
        val homepage = FeedDiscovery.homepage(xml, feedUrl)
        val name = FeedDiscovery.title(xml)?.take(MAX_NAME) ?: ArticleUrls.displayHost(homepage)
        return Result.Found(NewsSource(id, name, feedUrl, homepage, region, enabledByDefault = true))
    }

    private fun sameFeed(a: String, b: String) = a.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/') ==
        b.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')

    private companion object {
        const val MAX_CANDIDATES = 8
        const val MAX_NAME = 40
    }
}
