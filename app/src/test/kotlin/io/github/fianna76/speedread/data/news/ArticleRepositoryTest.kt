package io.github.fianna76.speedread.data.news

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.speedread.data.BookFileStore
import io.github.fianna76.speedread.data.BookFormat
import io.github.fianna76.speedread.data.OfflineBookRepository
import io.github.fianna76.speedread.data.ReadingPosition
import io.github.fianna76.speedread.data.db.SpeedReadDatabase
import io.github.fianna76.speedread.data.pdf.PdfBooks
import io.github.fianna76.speedread.testing.FakeWebClient
import io.github.fianna76.speedread.testing.TestNews
import kotlinx.coroutines.flow.first
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
class ArticleRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: SpeedReadDatabase
    private lateinit var files: BookFileStore
    private lateinit var books: OfflineBookRepository
    private lateinit var articles: ArticleRepository
    private val web = FakeWebClient()
    private var now = 10_000_000_000L

    // A known source, so the article is credited to its outlet.
    private val item = TestNews.item("bbc", "story-1", title = "Feed title")
        .copy(url = "https://bbc.example/news/story-1?at_medium=RSS")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, SpeedReadDatabase::class.java).build()
        files = BookFileStore(tmp.newFolder("files"))
        books = OfflineBookRepository(database.bookDao(), files, PdfBooks(context))
        articles = ArticleRepository(database.bookDao(), files, web, clock = { now })
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `opening an article saves its text for the reader without adding it to the library`() = runTest {
        web.responses[item.url] = TestNews.articlePage(paragraphs = 6)

        val result = articles.open(item) as ArticleResult.Ready

        val book = books.getBook(result.bookId)!!
        assertEquals("Feed title", book.title)
        assertEquals("BBC News", book.author)
        assertEquals(BookFormat.ARTICLE, book.format)
        assertEquals("https://bbc.example/news/story-1", book.sourceUrl)
        assertEquals(78, book.totalWords) // 6 paragraphs of 13 words.
        val chapter = books.getChapters(result.bookId).single()
        assertEquals(78, books.loadChapterText(result.bookId, chapter).size)
        assertTrue(books.observeBooks().first().isEmpty())
    }

    @Test
    fun `an article is downloaded once and its progress is exposed by URL`() = runTest {
        web.responses[item.url] = TestNews.articlePage()
        val id = (articles.open(item) as ArticleResult.Ready).bookId
        books.savePosition(id, ReadingPosition(0, 39), wordsRead = 39)

        // Same story, different tracking parameters.
        val again = articles.open(item.copy(url = "https://bbc.example/news/story-1?at_campaign=other"))

        assertEquals(ArticleResult.Ready(id), again)
        assertEquals(listOf(item.url), web.requests)
        assertEquals(mapOf("https://bbc.example/news/story-1" to 0.5f), articles.observeProgress().first())
    }

    @Test
    fun `download and extraction failures are reported`() = runTest {
        assertEquals(ArticleResult.Failed(ArticleError.NETWORK), articles.open(item))

        web.responses[item.url] = "<html><body><p>Watch the video above.</p></body></html>"
        assertEquals(ArticleResult.Failed(ArticleError.NO_TEXT), articles.open(item))

        web.responses[item.url] = TestNews.articlePage().replace(
            "</head>",
            "<script type=\"application/ld+json\">{\"isAccessibleForFree\": false}</script></head>",
        )
        assertEquals(ArticleResult.Failed(ArticleError.PAYWALLED), articles.open(item))

        assertNull(database.bookDao().findArticle(item.canonicalUrl))
    }

    @Test
    fun `an article whose text went missing is downloaded again`() = runTest {
        web.responses[item.url] = TestNews.articlePage()
        val first = (articles.open(item) as ArticleResult.Ready).bookId
        tmp.root.resolve("files/text-cache").deleteRecursively()

        val second = articles.open(item) as ArticleResult.Ready

        assertTrue(second.bookId != first)
        assertEquals(2, web.requests.size)
        assertNull(books.getBook(first))
    }

    @Test
    fun `articles not opened for a month are pruned with their text`() = runTest {
        web.responses[item.url] = TestNews.articlePage()
        val old = (articles.open(item) as ArticleResult.Ready).bookId
        val recentItem = TestNews.item("dw", "recent")
        web.responses[recentItem.url] = TestNews.articlePage()

        now += ArticleRepository.RETENTION_MILLIS - 1
        val recent = (articles.open(recentItem) as ArticleResult.Ready).bookId
        now += 2
        articles.pruneOld()

        assertNull(books.getBook(old))
        assertTrue(books.getBook(recent) != null)
        assertTrue(tmp.root.resolve("files/text-cache").list()!!.size == 1)
    }
}
