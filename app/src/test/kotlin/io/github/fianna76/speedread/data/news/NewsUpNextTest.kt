package io.github.fianna76.speedread.data.news

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.speedread.data.BookFileStore
import io.github.fianna76.speedread.data.db.SpeedReadDatabase
import io.github.fianna76.speedread.testing.FakeWebClient
import io.github.fianna76.speedread.testing.TestNews
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NewsUpNextTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: SpeedReadDatabase
    private val stories = listOf("a", "b", "c", "d").map { TestNews.item("rte", it) }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, SpeedReadDatabase::class.java).build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `the next story is the following unread one in the queue`() = runTest {
        val queue = NewsQueue().apply { set(stories) }
        val history = NewsHistoryRepository(database.newsHistoryDao())
        val web = FakeWebClient(mapOf(stories[2].url to TestNews.articlePage()))
        val articles = ArticleRepository(database.bookDao(), BookFileStore(tmp.newFolder()), web)
        val upNext = NewsUpNext(queue, history, articles, backgroundScope)
        history.markRead(stories[1])

        assertEquals(stories[2], upNext.nextAfter(stories[0].canonicalUrl))
        assertEquals(stories[3], upNext.nextAfter(stories[0].canonicalUrl, skip = setOf(stories[2].canonicalUrl)))
        assertNull(upNext.nextAfter(stories[3].canonicalUrl))
        assertNull(upNext.nextAfter("https://elsewhere.example/story"))

        assertTrue(upNext.open(stories[2]) is ArticleResult.Ready)
        assertEquals(stories[3], upNext.nextAfter(stories[0].canonicalUrl)) // Opening marks it read.
    }
}
