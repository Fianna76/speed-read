package io.github.fianna76.speedread.data.news

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.speedread.data.BookFileStore
import io.github.fianna76.speedread.data.OfflineBookRepository
import io.github.fianna76.speedread.data.db.SpeedReadDatabase
import io.github.fianna76.speedread.data.pdf.PdfBooks
import io.github.fianna76.speedread.testing.FakeWebClient
import io.github.fianna76.speedread.testing.TestNews
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedLinkReaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: SpeedReadDatabase
    private lateinit var files: BookFileStore
    private val web = FakeWebClient()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, SpeedReadDatabase::class.java).build()
        files = BookFileStore(tmp.newFolder())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `finds the web link in shared text`() {
        assertEquals("https://blog.example/post/1", SharedLinkReader.findUrl("Have a look: https://blog.example/post/1 via @app"))
        assertNull(SharedLinkReader.findUrl("Just some words, no link. example dot com"))
    }

    @Test
    fun `a shared article is extracted and titled from the page`() = runTest {
        web.responses["https://blog.example/post/1"] = TestNews.articlePage(title = "An interesting essay")
        val reader = SharedLinkReader(ArticleRepository(database.bookDao(), files, web), backgroundScope)

        assertTrue(reader.open("https://blog.example/post/1"))

        val ready = reader.state.first { it is SharedLinkReader.State.Ready } as SharedLinkReader.State.Ready
        val book = OfflineBookRepository(database.bookDao(), files, PdfBooks(context)).getBook(ready.bookId)!!
        assertEquals("An interesting essay", book.title)
        assertEquals("blog.example", book.author)
        reader.onOpened()
        assertEquals(SharedLinkReader.State.Idle, reader.state.value)
    }

    @Test
    fun `links from known outlets are credited to them, and failures are reported`() = runTest {
        val reader = SharedLinkReader(ArticleRepository(database.bookDao(), files, web), backgroundScope)

        assertTrue(reader.open("Read this https://www.rte.ie/news/2026/1017/story/", subject = "RTÉ story"))
        val failed = reader.state.first { it is SharedLinkReader.State.Failed } as SharedLinkReader.State.Failed
        assertEquals(ArticleError.NETWORK, failed.error)
        assertEquals("https://www.rte.ie/news/2026/1017/story/", failed.url)

        assertFalse(reader.open("no link here"))
    }
}
