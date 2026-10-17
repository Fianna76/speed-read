package io.github.fianna76.speedread.data.news.background

import io.github.fianna76.speedread.data.news.ArticleRepository
import io.github.fianna76.speedread.data.news.ArticleResult
import io.github.fianna76.speedread.data.news.NewsHistoryRepository
import io.github.fianna76.speedread.data.news.NewsRepository
import io.github.fianna76.speedread.news.FeedItem
import io.github.fianna76.speedread.news.NewsSource
import kotlinx.coroutines.flow.first

/**
 * The background sync: refreshes headlines, then downloads the reading list and the top
 * stories so they can be speed-read offline. Already-downloaded articles are not fetched
 * again, so a sync on an unchanged feed costs only the feed requests.
 */
class NewsPrefetcher(
    private val news: NewsRepository,
    private val articles: ArticleRepository,
    private val history: NewsHistoryRepository,
) {

    data class Result(val downloaded: Int, val failed: Int)

    suspend fun sync(sources: List<NewsSource>, topStories: Int = TOP_STORIES): Result {
        if (sources.isNotEmpty()) news.refresh(sources)
        val downloaded = articles.observeProgress().first().keys
        val read = history.observe().first()
        val saved = read.values.filter { it.isSaved }.sortedByDescending { it.savedAt }.map { it.item }
        // Top stories: each round of lead stories, home news first, skipping ones already read.
        val priority = sources.associate { it.id to it.region.ordinal }
        val top = news.state.value.headlines(sources.map { it.id }) { priority[it] ?: 0 }
            .filter { read[it.canonicalUrl]?.isRead != true }
            .take(topStories)
        return download(saved + top, downloaded)
    }

    /** Downloads [items] that aren't downloaded yet. */
    suspend fun download(items: List<FeedItem>, downloaded: Set<String>? = null): Result {
        val have = downloaded ?: articles.observeProgress().first().keys
        var done = 0
        var failed = 0
        for (item in items.distinctBy { it.canonicalUrl }) {
            if (item.canonicalUrl in have) continue
            when (articles.open(item)) {
                is ArticleResult.Ready -> done++
                is ArticleResult.Failed -> failed++
            }
        }
        return Result(done, failed)
    }

    companion object {
        /** Top stories downloaded per sync: enough for a commute, small enough to be polite. */
        const val TOP_STORIES = 8
    }
}
