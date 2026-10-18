package io.github.fianna76.speedread.data.news.background

import io.github.fianna76.speedread.data.news.NewsHistoryRepository
import io.github.fianna76.speedread.data.news.NewsRepository
import io.github.fianna76.speedread.data.news.briefing
import io.github.fianna76.speedread.data.settings.UserPreferences
import io.github.fianna76.speedread.news.AlertedStory
import io.github.fianna76.speedread.news.BreakingNews
import io.github.fianna76.speedread.news.Briefing
import io.github.fianna76.speedread.news.FeedItem
import io.github.fianna76.speedread.news.NewsRegion
import io.github.fianna76.speedread.news.NewsSource
import io.github.fianna76.speedread.news.StoryGroup
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/** Posts the app's news notifications. */
interface NewsNotifier {
    /** A breaking story; [outlets] are the names of the outlets leading with it. */
    fun breaking(story: StoryGroup<FeedItem>, outlets: List<String>)

    /** The morning briefing is ready; [stories] are its headlines. */
    fun briefing(stories: List<FeedItem>)
}

/** When and from where alerts may post. */
data class AlertOptions(
    val regions: Set<NewsRegion> = setOf(NewsRegion.IRELAND),
    val quietHours: Boolean = true,
    val dailyLimit: Int = UserPreferences.DEFAULT_ALERT_LIMIT,
) {
    companion object {
        fun of(prefs: UserPreferences) = AlertOptions(prefs.alertRegions, prefs.alertQuietHours, prefs.alertDailyLimit)
    }
}

/** Breaking-news alerts, checked after each background refresh (see [BreakingNews]). */
class BreakingNewsAlerts(
    private val news: NewsRepository,
    private val history: NewsHistoryRepository,
    private val log: NewsAlertLog,
    private val notifier: NewsNotifier,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {

    /**
     * Checks the (already refreshed) headlines of [sources] in the alert regions; returns how
     * many alerts were posted. In quiet hours nothing is posted or recorded, so a story still
     * fresh when they end alerts then.
     */
    suspend fun check(sources: List<NewsSource>, options: AlertOptions = AlertOptions()): Int {
        val watched = sources.filter { it.region in options.regions }
        if (watched.isEmpty()) return 0
        val now = clock()
        if (options.quietHours && isQuiet(now)) return 0
        val state = news.state.value
        // Outlets are grouped per region: a story counts when outlets of the same region lead with it.
        val read = history.observe().first().filterValues { it.isRead }.keys
        val snapshot = withContext(ioDispatcher) { log.read() }
        val found = watched.groupBy { it.region }.values.flatMap { regionSources ->
            val feeds = regionSources.associate { it.id to state.feeds[it.id]?.items.orEmpty() }
            BreakingNews.find(feeds, now, snapshot.alerted) { it.canonicalUrl in read }
        }
        val startOfDay = Instant.ofEpochMilli(now).atZone(zone()).toLocalDate().atStartOfDay(zone()).toInstant().toEpochMilli()
        val left = (options.dailyLimit - snapshot.announcedAt.count { it >= startOfDay }).coerceAtLeast(0)
        val announced = if (snapshot.isStarted) found.take(minOf(MAX_PER_CHECK, left)) else emptyList()
        val names = watched.associate { it.id to it.name }
        for (story in announced) {
            notifier.breaking(story, (listOf(story.lead) + story.related).mapNotNull { names[it.sourceId] }.distinct())
        }
        // Everything found is recorded, so what wasn't announced (or was leading the news when
        // alerts were switched on, or came after the day's limit) doesn't alert later.
        withContext(ioDispatcher) {
            log.record(
                found.flatMap { group -> (listOf(group.lead) + group.related).map { AlertedStory(it.canonicalUrl, it.title) } },
                announced = announced.mapTo(HashSet()) { it.lead.canonicalUrl },
            )
        }
        return announced.size
    }

    private fun isQuiet(now: Long): Boolean {
        val time = Instant.ofEpochMilli(now).atZone(zone()).toLocalTime()
        val minute = time.hour * 60 + time.minute
        return minute >= UserPreferences.QUIET_START || minute < UserPreferences.QUIET_END
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
    suspend fun prepare(
        sources: List<NewsSource>,
        perRegion: Int = Briefing.PER_REGION,
        regions: Set<NewsRegion> = NewsRegion.entries.toSet(),
    ): List<FeedItem> {
        val covered = sources.filter { it.region in regions }
        if (covered.isEmpty()) return emptyList()
        news.refresh(covered)
        val read = history.observe().first().filterValues { it.isRead }.keys
        val stories = news.state.value.briefing(covered, { it.canonicalUrl in read }, perRegion, regions).map { it.lead }
        if (stories.isEmpty()) return stories
        prefetcher.download(stories)
        notifier.briefing(stories)
        return stories
    }
}
