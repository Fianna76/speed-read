package io.github.fianna76.speedread.data.news

import io.github.fianna76.speedread.news.FeedItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The stories around the one being read: the Newsstand list (feed, filter or reading list) it
 * was opened from, in display order. App-scoped so the reader can offer the next story.
 */
class NewsQueue {
    @Volatile
    var items: List<FeedItem> = emptyList()
        private set

    fun set(items: List<FeedItem>) {
        this.items = items.distinctBy { it.canonicalUrl }
    }
}

/** What the reader needs to offer "next story" after a news article. */
interface UpNext {
    /** The next unread story after [url] in the queue, skipping [skip] (canonical URLs). */
    suspend fun nextAfter(url: String, skip: Set<String> = emptySet()): FeedItem?

    /** Downloads (if needed) and marks [item] read; the result opens in the reader. */
    suspend fun open(item: FeedItem): ArticleResult

    /** Downloads [item] in the background so it opens instantly; failures are ignored. */
    fun prefetch(item: FeedItem)
}

class NewsUpNext(
    private val queue: NewsQueue,
    private val history: NewsHistoryRepository,
    private val articles: ArticleRepository,
    private val scope: CoroutineScope,
) : UpNext {

    override suspend fun nextAfter(url: String, skip: Set<String>): FeedItem? {
        val items = queue.items
        val index = items.indexOfFirst { it.canonicalUrl == url }
        if (index < 0) return null
        val read = history.observe().first().filterValues { it.isRead }.keys
        return items.drop(index + 1).firstOrNull { it.canonicalUrl !in read && it.canonicalUrl !in skip }
    }

    override suspend fun open(item: FeedItem): ArticleResult = articles.open(item).also { result ->
        if (result is ArticleResult.Ready) history.markRead(item)
    }

    override fun prefetch(item: FeedItem) {
        scope.launch { articles.open(item) }
    }
}
