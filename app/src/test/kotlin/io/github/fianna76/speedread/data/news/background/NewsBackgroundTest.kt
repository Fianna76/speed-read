package io.github.fianna76.speedread.data.news.background

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.fianna76.speedread.data.BookFileStore
import io.github.fianna76.speedread.data.db.SpeedReadDatabase
import io.github.fianna76.speedread.data.news.ArticleRepository
import io.github.fianna76.speedread.data.news.NewsCache
import io.github.fianna76.speedread.data.news.NewsHistoryRepository
import io.github.fianna76.speedread.data.news.NewsRepository
import io.github.fianna76.speedread.news.FeedItem
import io.github.fianna76.speedread.news.NewsRegion
import io.github.fianna76.speedread.news.StoryGroup
import io.github.fianna76.speedread.testing.FakeWebClient
import io.github.fianna76.speedread.testing.TestNews
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class NewsBackgroundTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: SpeedReadDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, SpeedReadDatabase::class.java).build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `a sync refreshes feeds and downloads saved and top unread stories once`() = runTest {
        val rte = TestNews.source("rte")
        val web = FakeWebClient(mapOf(rte.feedUrl to TestNews.rss("rte", "a", "b", "c")))
        listOf("a", "b", "c", "saved").forEach { web.responses["https://rte.example/news/$it"] = TestNews.articlePage() }
        val news = NewsRepository(web, NewsCache(tmp.root.resolve("news.json")))
        val articles = ArticleRepository(database.bookDao(), BookFileStore(tmp.newFolder()), web)
        val history = NewsHistoryRepository(database.newsHistoryDao())
        history.setSaved(TestNews.item("rte", "saved"), true)
        history.markRead(TestNews.item("rte", "b"))
        val prefetcher = NewsPrefetcher(news, articles, history)

        val first = prefetcher.sync(listOf(rte), topStories = 2)

        assertEquals(NewsPrefetcher.Result(downloaded = 3, failed = 0), first) // Saved, a and c (b was read).
        assertEquals(
            setOf("https://rte.example/news/saved", "https://rte.example/news/a", "https://rte.example/news/c"),
            articles.observeProgress().first().keys,
        )

        web.requests.clear()
        assertEquals(NewsPrefetcher.Result(0, 0), prefetcher.sync(listOf(rte), topStories = 2))
        assertEquals(listOf(rte.feedUrl), web.requests)
    }

    @Test
    fun `the scheduler runs downloads on unmetered networks, alerts hourly on any network, and can be cancelled`() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val scheduler = NewsBackgroundScheduler(context)
        val workManager = WorkManager.getInstance(context)
        fun scheduled() = workManager.getWorkInfosForUniqueWork(NewsBackgroundScheduler.WORK_NAME).get().single()

        scheduler.apply(NewsSyncConfig(downloads = true, alerts = false))
        assertEquals(WorkInfo.State.ENQUEUED, scheduled().state)
        assertEquals(NetworkType.UNMETERED, scheduled().constraints.requiredNetworkType)
        assertTrue(scheduled().constraints.requiresBatteryNotLow())
        assertEquals(TimeUnit.HOURS.toMillis(4), scheduled().periodicityInfo!!.repeatIntervalMillis)

        scheduler.apply(NewsSyncConfig(downloads = true, alerts = true))
        assertEquals(NetworkType.CONNECTED, scheduled().constraints.requiredNetworkType)
        assertEquals(TimeUnit.HOURS.toMillis(1), scheduled().periodicityInfo!!.repeatIntervalMillis)

        scheduler.apply(NewsSyncConfig.NONE)
        assertEquals(WorkInfo.State.CANCELLED, scheduled().state)
    }

    @Test
    fun `the briefing is scheduled daily from the next chosen time`() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val scheduler = NewsBackgroundScheduler(context)
        val workManager = WorkManager.getInstance(context)
        fun scheduled() = workManager.getWorkInfosForUniqueWork(NewsBackgroundScheduler.BRIEFING_WORK_NAME).get()

        scheduler.applyBriefing(7 * 60, now = LocalDateTime.of(2026, 10, 17, 6, 0))
        val work = scheduled().single()
        assertEquals(WorkInfo.State.ENQUEUED, work.state)
        assertEquals(TimeUnit.DAYS.toMillis(1), work.periodicityInfo!!.repeatIntervalMillis)
        assertEquals(TimeUnit.HOURS.toMillis(1), work.initialDelayMillis)

        scheduler.applyBriefing(null)
        assertTrue(scheduled().all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun `the delay runs to the next occurrence of the briefing time`() {
        val at = LocalDateTime.of(2026, 10, 17, 7, 30)
        assertEquals(Duration.ofMinutes(30), NewsBackgroundScheduler.delayUntil(8 * 60, at))
        assertEquals(Duration.ofHours(23).plusMinutes(30), NewsBackgroundScheduler.delayUntil(7 * 60, at))
        assertEquals(Duration.ofDays(1), NewsBackgroundScheduler.delayUntil(7 * 60 + 30, at))
    }

    @Test
    fun `breaking alerts start quietly, then announce a story led by two outlets once`() = runTest {
        val rte = TestNews.source("rte").copy(region = NewsRegion.IRELAND)
        val times = TestNews.source("irishtimes").copy(region = NewsRegion.IRELAND)
        val bbc = TestNews.source("bbc")
        val web = FakeWebClient(
            mapOf(
                rte.feedUrl to rss("rte", "old" to "Minister resigns over housing grants controversy"),
                times.feedUrl to rss("irishtimes", "old" to "Housing minister resigns amid grants controversy"),
                bbc.feedUrl to rss("bbc", "x" to "Hurricane hits Mexico coast", "y" to "Hurricane hits Mexico coast hard"),
            ),
        )
        val news = NewsRepository(web, NewsCache(tmp.root.resolve("news.json")))
        val history = NewsHistoryRepository(database.newsHistoryDao())
        val notifier = RecordingNotifier()
        val alerts = BreakingNewsAlerts(news, history, NewsAlertLog(tmp.root.resolve("alerts.json")), notifier)
        val sources = listOf(rte, times, bbc)

        news.refresh(sources)
        assertEquals(0, alerts.check(sources)) // First check: what is already leading is not news.
        assertTrue(notifier.breaking.isEmpty())

        web.responses[rte.feedUrl] = rss("rte", "fire" to "Major fire closes Dublin Port overnight", "old" to "Minister resigns")
        web.responses[times.feedUrl] = rss("irishtimes", "fire" to "Dublin Port closed by major overnight fire")
        news.refresh(sources)
        assertEquals(1, alerts.check(sources))
        assertEquals("Major fire closes Dublin Port overnight", notifier.breaking.single().first.lead.title)
        assertEquals(listOf("RTE", "IRISHTIMES"), notifier.breaking.single().second)

        assertEquals(0, alerts.check(sources)) // Announced once.
    }

    @Test
    fun `the morning briefing downloads its stories and announces them`() = runTest {
        val rte = TestNews.source("rte").copy(region = NewsRegion.IRELAND)
        val bbc = TestNews.source("bbc")
        val web = FakeWebClient(mapOf(rte.feedUrl to TestNews.rss("rte", "a", "b", "c"), bbc.feedUrl to TestNews.rss("bbc", "d")))
        listOf("rte" to "a", "rte" to "b", "rte" to "c", "bbc" to "d").forEach { (id, slug) ->
            web.responses["https://$id.example/news/$slug"] = TestNews.articlePage()
        }
        val news = NewsRepository(web, NewsCache(tmp.root.resolve("news.json")))
        val articles = ArticleRepository(database.bookDao(), BookFileStore(tmp.newFolder()), web)
        val history = NewsHistoryRepository(database.newsHistoryDao())
        history.markRead(TestNews.item("rte", "a"))
        val notifier = RecordingNotifier()
        val briefing = MorningBriefing(news, NewsPrefetcher(news, articles, history), history, notifier)

        val stories = briefing.prepare(listOf(rte, bbc))

        assertEquals(listOf("Story b", "Story c", "Story d"), stories.map { it.title })
        assertEquals(stories, notifier.briefings.single())
        assertEquals(stories.map { it.canonicalUrl }.toSet(), articles.observeProgress().first().keys)
    }

    private fun rss(sourceId: String, vararg items: Pair<String, String>): String =
        "<rss><channel><title>$sourceId</title>" +
            items.joinToString("") { (slug, title) -> "<item><title>$title</title><link>https://$sourceId.example/news/$slug</link></item>" } +
            "</channel></rss>"

    private class RecordingNotifier : NewsNotifier {
        val breaking = mutableListOf<Pair<StoryGroup<FeedItem>, List<String>>>()
        val briefings = mutableListOf<List<FeedItem>>()

        override fun breaking(story: StoryGroup<FeedItem>, outlets: List<String>) {
            breaking += story to outlets
        }

        override fun briefing(stories: List<FeedItem>) {
            briefings += stories
        }
    }
}
