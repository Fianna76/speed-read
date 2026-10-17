package io.github.fianna76.speedread.data.news.background

import io.github.fianna76.speedread.data.news.NewsHistoryRepository
import io.github.fianna76.speedread.data.news.NewsRepository
import io.github.fianna76.speedread.data.news.briefing
import io.github.fianna76.speedread.news.AlertedStory
import io.github.fianna76.speedread.news.BreakingNews
import io.github.fianna76.speedread.news.FeedItem
import io.github.fianna76.speedread.news.NewsRegion
import io.github.fianna76.speedread.news.NewsSource
import io.github.fianna76.speedread.news.StoryGroup
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Posts the app's news notifications. */
interface NewsNotifier {
    /** A breaking story; [outlets] are the names of the outlets leading with it. */
    fun breaking(story: StoryGroup<FeedItem>, outlets: List<String>)

    /** The morning briefing is ready; [stories] are its headlines. */
    fun briefing(stories: List<FeedItem>)
}

/** Breaking-news alerts, checked after each background refresh (see [BreakingNews]). */
class BreakingNewsAlerts(
    private val news: NewsRepository,
    private val history: NewsHistoryRepository,
    private val log: NewsAlertLog,
    private val notifier: NewsNotifier,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /** Checks the (already refreshed) headlines of [sources]' Irish outlets; returns how many alerts were posted. */
    suspend fun check(sources: List<NewsSource>): Int {
        val irish = sources.filter { it.region == NewsRegion.IRELAND }
        if (irish.isEmpty()) return 0
        val state = news.state.value
        val feeds = irish.associate { it.id to state.feeds[it.id]?.items.orEmpty() }
        val read = history.observe().first().filterValues { it.isRead }.keys
        val snapshot = withContext(ioDispatcher) { log.read() }
        val found = BreakingNews.find(feeds, clock(), snapshot.alerted) { it.canonicalUrl in read }
        val announced = if (snapshot.isStarted) found.take(MAX_PER_CHECK) else emptyList()
        val names = irish.associate { it.id to it.name }
        for (story in announced) {
            notifier.breaking(story, (listOf(story.lead) + story.related).mapNotNull { names[it.sourceId] }.distinct())
        }
        // Everything found is recorded, so what wasn't announced (or was leading the news when
        // alerts were switched on) doesn't alert later.
        withContext(ioDispatcher) {
            log.record(found.flatMap { group -> (listOf(group.lead) + group.related).map { AlertedStory(it.canonicalUrl, it.title) } })
        }
        return announced.size
    }

    companion object {
        /** At most this many alerts per check: a big news day shouldn't flood the shade. */
        const val MAX_PER_CHECK = 2
    }
}

/** The morning briefing: refresh, download the briefing's stories, then notify. */
class MorningBriefing(
    private val news: NewsRepository,
    private val prefetcher: NewsPrefetcher,
    private val history: NewsHistoryRepository,
    private val notifier: NewsNotifier,
) {

    /** Returns the briefing's stories (empty if there was nothing unread to brief). */
    suspend fun prepare(sources: List<NewsSource>): List<FeedItem> {
        if (sources.isEmpty()) return emptyList()
        news.refresh(sources)
        val read = history.observe().first().filterValues { it.isRead }.keys
        val stories = news.state.value.briefing(sources) { it.canonicalUrl in read }.map { it.lead }
        if (stories.isEmpty()) return stories
        prefetcher.download(stories)
        notifier.briefing(stories)
        return stories
    }
}
