package io.github.fianna76.speedread.ui.news

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.speedread.data.BookFileStore
import io.github.fianna76.speedread.data.db.SpeedReadDatabase
import io.github.fianna76.speedread.data.news.ArticleError
import io.github.fianna76.speedread.data.news.ArticleRepository
import io.github.fianna76.speedread.data.news.NewsCache
import io.github.fianna76.speedread.data.news.NewsRepository
import io.github.fianna76.speedread.data.settings.UserPreferencesRepository
import io.github.fianna76.speedread.news.NewsSources
import io.github.fianna76.speedread.testing.FakeWebClient
import io.github.fianna76.speedread.testing.MainDispatcherRule
import io.github.fianna76.speedread.testing.TestNews
import io.github.fianna76.speedread.testing.testPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
class NewsstandViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: SpeedReadDatabase
    private val bbc = NewsSources.byId("bbc")!!
    private val dw = NewsSources.byId("dw")!!
    private val web = FakeWebClient(
        mapOf(bbc.feedUrl to TestNews.rss("bbc", "b1", "b2"), dw.feedUrl to TestNews.rss("dw", "d1")),
    )
    private lateinit var preferences: UserPreferencesRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, SpeedReadDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    /** A Newsstand showing only the BBC and DW (every other source switched off). */
    private suspend fun TestScope.createViewModel(): NewsstandViewModel {
        preferences = testPreferences(tmp.newFolder(), backgroundScope)
        NewsSources.ALL.filter { it != bbc && it != dw }.forEach { preferences.setNewsSourceEnabled(it.id, false) }
        val files = BookFileStore(tmp.newFolder("files"))
        val viewModel = NewsstandViewModel(
            news = NewsRepository(web, NewsCache(tmp.root.resolve("news.json"))),
            articles = ArticleRepository(database.bookDao(), files, web),
            preferences = preferences,
            externalScope = backgroundScope,
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        return viewModel
    }

    private suspend fun NewsstandViewModel.awaitState(predicate: (NewsstandUiState) -> Boolean) = uiState.first(predicate)

    @Test
    fun `loads headlines from the enabled sources on open`() = runTest {
        val viewModel = createViewModel()

        val state = viewModel.awaitState { it.hasLoaded && it.articles.isNotEmpty() }

        assertEquals(listOf("bbc", "dw"), state.sources.map { it.id })
        assertEquals(listOf("b1", "d1", "b2"), state.articles.map { it.item.url.substringAfterLast('/') })
        assertEquals("BBC News", state.articles.first().source.name)
        assertTrue(web.requests.none { "aljazeera" in it })
    }

    @Test
    fun `filters by source`() = runTest {
        val viewModel = createViewModel()
        viewModel.awaitState { it.articles.size == 3 }

        viewModel.selectSource("dw")

        val state = viewModel.awaitState { it.selectedSourceId == "dw" }
        assertEquals(listOf("d1"), state.articles.map { it.item.url.substringAfterLast('/') })
    }

    @Test
    fun `reading in the app saves the article and opens the reader`() = runTest {
        val viewModel = createViewModel()
        val article = viewModel.awaitState { it.articles.isNotEmpty() }.articles.first()
        web.responses[article.item.url] = TestNews.articlePage()

        viewModel.readInApp(article.item)

        val bookId = viewModel.awaitState { it.bookToOpen != null }.bookToOpen!!
        viewModel.onReaderOpened()
        val state = viewModel.awaitState { it.bookToOpen == null && it.articles.first().progress != null }
        assertEquals(0f, state.articles.first().progress)
        assertNull(state.preparingUrl)
        assertEquals("Story b1", database.bookDao().findById(bookId)!!.title)
    }

    @Test
    fun `articles that cannot be read in the app explain why`() = runTest {
        val viewModel = createViewModel()
        val article = viewModel.awaitState { it.articles.isNotEmpty() }.articles.first()

        viewModel.readInApp(article.item)

        val message = viewModel.awaitState { it.message != null }.message!!
        assertEquals(ArticleError.NETWORK, message.error)
        assertEquals(article.item, message.item)
        viewModel.onMessageShown(message.id)
        assertNull(viewModel.awaitState { it.message == null }.message)
    }

    @Test
    fun `failing sources are listed and switched-off sources disappear`() = runTest {
        web.responses.remove(dw.feedUrl)
        val viewModel = createViewModel()

        val state = viewModel.awaitState { it.hasLoaded && !it.isRefreshing && it.articles.isNotEmpty() }
        assertEquals(listOf("dw"), state.failedSources.map { it.id })

        preferences.setNewsSourceEnabled("bbc", false)
        preferences.setNewsSourceEnabled("dw", false)
        val empty = viewModel.awaitState { it.sources.isEmpty() }
        assertTrue(empty.articles.isEmpty())
    }
}
