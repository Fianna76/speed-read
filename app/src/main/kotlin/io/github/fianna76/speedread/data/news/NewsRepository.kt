package io.github.fianna76.speedread.data.news

import android.util.Log
import io.github.fianna76.speedread.news.FeedItem
import io.github.fianna76.speedread.news.FeedParser
import io.github.fianna76.speedread.news.NewsSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The latest headlines of one source. */
data class SourceFeed(val items: List<FeedItem>, val fetchedAt: Long)

data class NewsFeedState(
    /** Latest headlines by source id. */
    val feeds: Map<String, SourceFeed> = emptyMap(),
    /** Sources whose most recent refresh failed (their older headlines, if any, are kept). */
    val failedSources: Set<String> = emptySet(),
    val isRefreshing: Boolean = false,
) {
    /**
     * Headlines of [sourceIds], interleaved by feed position: every outlet's lead story, then
     * every second story, and so on. Feeds list top stories in editorial order, so this keeps
     * each outlet's priorities and stops a busy feed crowding out the rest. Within a round,
     * stories are ordered by [priority] (lower first, e.g. home news before world news), then
     * newest first.
     */
    fun headlines(sourceIds: Collection<String>, priority: (sourceId: String) -> Int = { 0 }): List<FeedItem> {
        val lists = sourceIds.mapNotNull { feeds[it]?.items?.takeIf(List<FeedItem>::isNotEmpty) }
        val rounds = lists.maxOfOrNull { it.size } ?: 0
        val order = compareBy<FeedItem> { priority(it.sourceId) }
            .thenByDescending(nullsFirst()) { it.publishedAt }
        return (0 until rounds)
            .flatMap { round -> lists.mapNotNull { it.getOrNull(round) }.sortedWith(order) }
            .distinctBy { it.canonicalUrl }
    }

    fun lastUpdated(sourceIds: Collection<String>): Long? = sourceIds.mapNotNull { feeds[it]?.fetchedAt }.maxOrNull()
}

/**
 * Fetches and caches the Newsstand's headlines. Sources are fetched in parallel and fail
 * independently: one outlet being down never hides the others. The last good headlines are
 * kept on disk so the Newsstand opens instantly, and offline.
 */
class NewsRepository(
    private val web: WebClient,
    private val cache: NewsCache,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Called after a refresh that fetched at least one feed (e.g. to update widgets). */
    private val onUpdated: () -> Unit = {},
) {
    private val _state = MutableStateFlow(NewsFeedState())
    val state: StateFlow<NewsFeedState> = _state.asStateFlow()

    private val refreshMutex = Mutex()
    private var cacheLoaded = false

    /** Refreshes [sources] whose headlines are missing or older than [maxAgeMillis]. */
    suspend fun refreshIfStale(sources: List<NewsSource>, maxAgeMillis: Long = STALE_AFTER_MILLIS) {
        loadCache()
        val now = clock()
        val stale = sources.filter { source ->
            val fetchedAt = _state.value.feeds[source.id]?.fetchedAt
            fetchedAt == null || now - fetchedAt >= maxAgeMillis || fetchedAt > now
        }
        if (stale.isNotEmpty()) refresh(stale)
    }

    /** Fetches [sources] now. A refresh already in progress is joined rather than repeated. */
    suspend fun refresh(sources: List<NewsSource>) {
        loadCache()
        if (sources.isEmpty()) return
        if (!refreshMutex.tryLock()) {
            refreshMutex.withLock { } // Wait for the running refresh to finish.
            return
        }
        try {
            _state.update { it.copy(isRefreshing = true) }
            val results = coroutineScope {
                sources.map { source -> async { source to fetch(source) } }.awaitAll()
            }
            val fetchedAt = clock()
            _state.update { state ->
                val feeds = state.feeds.toMutableMap()
                val failed = state.failedSources.toMutableSet()
                for ((source, items) in results) {
                    if (items == null) {
                        failed += source.id
                    } else {
                        feeds[source.id] = SourceFeed(items, fetchedAt)
                        failed -= source.id
                    }
                }
                state.copy(feeds = feeds, failedSources = failed)
            }
            withContext(ioDispatcher) { cache.write(_state.value.feeds) }
            if (results.any { it.second != null }) onUpdated()
        } finally {
            _state.update { it.copy(isRefreshing = false) }
            refreshMutex.unlock()
        }
    }

    private suspend fun fetch(source: NewsSource): List<FeedItem>? = try {
        val xml = web.fetchText(source.feedUrl)
        withContext(Dispatchers.Default) { FeedParser.parse(xml, source, MAX_ITEMS_PER_SOURCE) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Could not refresh ${source.id}", e)
        null
    }

    /** Loads cached headlines if that hasn't happened yet (e.g. for a widget before the app opens). */
    suspend fun ensureLoaded() = loadCache()

    private suspend fun loadCache() {
        if (cacheLoaded) return
        val cached = withContext(ioDispatcher) { cache.read() }
        cacheLoaded = true
        // Fresh results from a refresh that raced the cache read win.
        _state.update { it.copy(feeds = cached + it.feeds) }
    }

    companion object {
        private const val TAG = "NewsRepository"
        const val MAX_ITEMS_PER_SOURCE = 20
        const val STALE_AFTER_MILLIS = 15 * 60 * 1000L
    }
}
