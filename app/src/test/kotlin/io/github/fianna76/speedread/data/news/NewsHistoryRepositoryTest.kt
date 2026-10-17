package io.github.fianna76.speedread.data.news

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.speedread.data.BookFileStore
import io.github.fianna76.speedread.data.db.SpeedReadDatabase
import io.github.fianna76.speedread.testing.FakeWebClient
import io.github.fianna76.speedread.testing.TestNews
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NewsHistoryRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: SpeedReadDatabase
    private lateinit var history: NewsHistoryRepository
    private var now = 10_000_000_000L
    private val item = TestNews.item("dw", "pope", "Pope leaves hospital").copy(url = "https://dw.example/news/pope?maca=rss")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, SpeedReadDatabase::class.java).build()
        history = NewsHistoryRepository(database.newsHistoryDao(), clock = { now })
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `reading and saving are remembered by canonical URL with the headline`() = runTest {
        history.markRead(item)
        now += 5
        history.setSaved(item.copy(title = "Pope Leo leaves hospital"), true)

        val entry = history.observe().first().getValue("https://dw.example/news/pope")
        assertEquals("Pope Leo leaves hospital", entry.item.title)
        assertEquals(item.url, entry.item.url)
        assertEquals(now - 5, entry.readAt)
        assertEquals(now, entry.savedAt)
    }

    @Test
    fun `unsaving a story that was never read forgets it`() = runTest {
        history.setSaved(item, true)
        history.setSaved(item, false)

        assertTrue(history.observe().first().isEmpty())
    }

    @Test
    fun `old unsaved history is pruned but saved stories and their text are kept`() = runTest {
        val other = TestNews.item("bbc", "old")
        history.markRead(other)
        history.markRead(item)
        history.setSaved(item, true)
        val web = FakeWebClient(mapOf(item.url to TestNews.articlePage(), other.url to TestNews.articlePage()))
        val articles = ArticleRepository(database.bookDao(), BookFileStore(tmp.newFolder()), web, clock = { now })
        articles.open(item)
        articles.open(other)

        now += ArticleRepository.RETENTION_MILLIS + 1
        history.pruneOld()
        articles.pruneOld()

        val entries = history.observe().first()
        assertNotNull(entries["https://dw.example/news/pope"])
        assertNull(entries[other.canonicalUrl])
        assertNotNull(database.bookDao().findArticle("https://dw.example/news/pope"))
        assertNull(database.bookDao().findArticle(other.canonicalUrl))
    }
}
