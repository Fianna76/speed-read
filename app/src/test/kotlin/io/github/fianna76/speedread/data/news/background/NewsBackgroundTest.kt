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
    fun `the scheduler runs the sync on unmetered networks only, and can be cancelled`() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val scheduler = NewsBackgroundScheduler(context)
        val workManager = WorkManager.getInstance(context)

        scheduler.apply(true)
        val scheduled = workManager.getWorkInfosForUniqueWork(NewsBackgroundScheduler.WORK_NAME).get().single()
        assertEquals(WorkInfo.State.ENQUEUED, scheduled.state)
        assertEquals(NetworkType.UNMETERED, scheduled.constraints.requiredNetworkType)
        assertTrue(scheduled.constraints.requiresBatteryNotLow())

        scheduler.apply(false)
        val cancelled = workManager.getWorkInfosForUniqueWork(NewsBackgroundScheduler.WORK_NAME).get().single()
        assertEquals(WorkInfo.State.CANCELLED, cancelled.state)
    }
}
