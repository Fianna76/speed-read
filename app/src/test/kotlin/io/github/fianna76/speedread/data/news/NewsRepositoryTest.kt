package io.github.fianna76.speedread.data.news

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.speedread.testing.FakeWebClient
import io.github.fianna76.speedread.testing.TestNews
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NewsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val bbc = TestNews.source("bbc")
    private val dw = TestNews.source("dw")
    private val web = FakeWebClient(
        mapOf(
            bbc.feedUrl to TestNews.rss("bbc", "b1", "b2", "b3"),
            dw.feedUrl to TestNews.rss("dw", "d1"),
        ),
    )
    private var now = 1_000_000L

    private fun TestScope.repository(cacheFile: File = File(tmp.root, "news/headlines.json")) =
        NewsRepository(web, NewsCache(cacheFile), StandardTestDispatcher(testScheduler), clock = { now })

    @Test
    fun `refresh fetches every source and interleaves their headlines`() = runTest {
        val repository = repository()

        repository.refresh(listOf(bbc, dw))

        val state = repository.state.value
        assertEquals(listOf("b1", "d1", "b2", "b3"), state.headlines(listOf("bbc", "dw")).map { it.url.substringAfterLast('/') })
        assertEquals(listOf("d1"), state.headlines(listOf("dw")).map { it.url.substringAfterLast('/') })
        assertEquals(now, state.lastUpdated(listOf("bbc")))
        assertFalse(state.isRefreshing)
        assertTrue(state.failedSources.isEmpty())
    }

    @Test
    fun `newer stories lead within each round`() {
        val state = NewsFeedState(
            feeds = mapOf(
                "bbc" to SourceFeed(listOf(TestNews.item("bbc", "old", publishedAt = 1), TestNews.item("bbc", "b2")), 0),
                "dw" to SourceFeed(listOf(TestNews.item("dw", "new", publishedAt = 2)), 0),
            ),
        )

        assertEquals(listOf("new", "old", "b2"), state.headlines(listOf("bbc", "dw")).map { it.url.substringAfterLast('/') })
    }

    @Test
    fun `within a round, higher-priority sources lead`() {
        val state = NewsFeedState(
            feeds = mapOf(
                "world" to SourceFeed(listOf(TestNews.item("world", "w1", publishedAt = 9), TestNews.item("world", "w2")), 0),
                "home" to SourceFeed(listOf(TestNews.item("home", "h1", publishedAt = 1), TestNews.item("home", "h2")), 0),
            ),
        )

        val ordered = state.headlines(listOf("world", "home")) { id -> if (id == "home") 0 else 1 }

        assertEquals(listOf("h1", "w1", "h2", "w2"), ordered.map { it.url.substringAfterLast('/') })
    }

    @Test
    fun `a failing source is reported without hiding the others or its older headlines`() = runTest {
        val repository = repository()
        repository.refresh(listOf(bbc, dw))

        web.responses.remove(dw.feedUrl)
        now += 60_000
        repository.refresh(listOf(bbc, dw))

        val state = repository.state.value
        assertEquals(setOf("dw"), state.failedSources)
        assertEquals(listOf("b1", "d1", "b2", "b3"), state.headlines(listOf("bbc", "dw")).map { it.url.substringAfterLast('/') })

        web.responses[dw.feedUrl] = TestNews.rss("dw", "d2")
        repository.refresh(listOf(dw))
        assertTrue(repository.state.value.failedSources.isEmpty())
        assertEquals(listOf("d2"), repository.state.value.headlines(listOf("dw")).map { it.url.substringAfterLast('/') })
    }

    @Test
    fun `a feed that is not RSS counts as a failure`() = runTest {
        web.responses[bbc.feedUrl] = "<html><body>Maintenance</body></html>"
        val repository = repository()

        repository.refresh(listOf(bbc))

        assertEquals(setOf("bbc"), repository.state.value.failedSources)
    }

    @Test
    fun `refreshIfStale only fetches sources with old or missing headlines`() = runTest {
        val repository = repository()
        repository.refresh(listOf(bbc))
        web.requests.clear()

        now += 60_000
        repository.refreshIfStale(listOf(bbc, dw))
        assertEquals(listOf(dw.feedUrl), web.requests)

        web.requests.clear()
        now += NewsRepository.STALE_AFTER_MILLIS
        repository.refreshIfStale(listOf(bbc, dw))
        assertEquals(setOf(bbc.feedUrl, dw.feedUrl), web.requests.toSet())
    }

    @Test
    fun `headlines survive a restart through the cache`() = runTest {
        val cacheFile = File(tmp.root, "cache/headlines.json")
        repository(cacheFile).refresh(listOf(bbc, dw))

        web.responses.clear()
        web.requests.clear()
        val restarted = repository(cacheFile)
        restarted.refreshIfStale(listOf(bbc, dw))

        assertTrue(web.requests.isEmpty())
        assertEquals(4, restarted.state.value.headlines(listOf("bbc", "dw")).size)
        assertEquals(now, restarted.state.value.lastUpdated(listOf("dw")))
    }

    @Test
    fun `an unreadable cache is ignored`() = runTest {
        val cacheFile = tmp.newFile("headlines.json").apply { writeText("{not json") }

        val repository = repository(cacheFile)
        repository.refreshIfStale(listOf(bbc))

        assertEquals(3, repository.state.value.headlines(listOf("bbc")).size)
    }
}
