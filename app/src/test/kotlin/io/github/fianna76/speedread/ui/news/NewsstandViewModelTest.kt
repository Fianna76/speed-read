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
import io.github.fianna76.speedread.data.news.NewsHistoryRepository
import io.github.fianna76.speedread.data.news.NewsRepository
import io.github.fianna76.speedread.data.settings.NewsImages
import io.github.fianna76.speedread.data.settings.UserPreferencesRepository
import io.github.fianna76.speedread.news.NewsRegion
import io.github.fianna76.speedread.news.NewsSources
import io.github.fianna76.speedread.testing.FakeWebClient
import io.github.fianna76.speedread.testing.MainDispatcherRule
import io.github.fianna76.speedread.testing.TestNews
import io.github.fianna76.speedread.testing.testPreferences
import kotlinx.coroutines.flow.MutableStateFlow
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

    private val metered = MutableStateFlow(false)

    /** A Newsstand showing only the BBC and DW (every other source switched off). */
    private suspend fun TestScope.createViewModel(): NewsstandViewModel {
        preferences = testPreferences(tmp.newFolder(), backgroundScope)
        NewsSources.ALL.filter { it != bbc && it != dw }.forEach { preferences.setNewsSourceEnabled(it.id, false) }
        val files = BookFileStore(tmp.newFolder("files"))
        val viewModel = NewsstandViewModel(
            news = NewsRepository(web, NewsCache(tmp.root.resolve("news.json"))),
            articles = ArticleRepository(database.bookDao(), files, web),
            history = NewsHistoryRepository(database.newsHistoryDao()),
            preferences = preferences,
            externalScope = backgroundScope,
            isMetered = metered,
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        return viewModel
    }

    private suspend fun NewsstandViewModel.awaitState(predicate: (NewsstandUiState) -> Boolean) = uiState.first(predicate)

    @Test
    fun `loads headlines from the enabled sources on open`() = runTest {
        val viewModel = createViewModel()

        val state = viewModel.awaitState { it.hasLoaded && it.articles.isNotEmpty() }

        assertEquals(listOf("dw", "bbc"), state.sources.map { it.id }) // Europe before world.
        // Each round of lead stories puts European news (DW) before world news (BBC).
        assertEquals(listOf("d1", "b1", "b2"), state.articles.map { it.item.url.substringAfterLast('/') })
        assertEquals(listOf(NewsRegion.EUROPE, NewsRegion.WORLD), state.regions)
        assertEquals("DW", state.articles.first().source.name)
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
    fun `filters by region, and a region change clears the source filter`() = runTest {
        val viewModel = createViewModel()
        viewModel.awaitState { it.articles.size == 3 }
        viewModel.selectSource("dw")
        viewModel.awaitState { it.selectedSourceId == "dw" }

        viewModel.selectRegion(NewsRegion.WORLD)

        val state = viewModel.awaitState { it.selectedRegion == NewsRegion.WORLD }
        assertNull(state.selectedSourceId)
        assertEquals(listOf("bbc"), state.sourcesInRegion.map { it.id })
        assertEquals(listOf("b1", "b2"), state.articles.map { it.item.url.substringAfterLast('/') })
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
        assertEquals("Story d1", database.bookDao().findById(bookId)!!.title)
    }

    @Test
    fun `articles that cannot be read in the app explain why`() = runTest {
        val viewModel = createViewModel()
        val article = viewModel.awaitState { it.articles.isNotEmpty() }.articles.first()

        viewModel.readInApp(article.item)

        val message = viewModel.awaitState { it.message != null }.message as NewsMessage.ArticleFailed
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

    private fun rssWithTitles(sourceId: String, vararg titles: Pair<String, String>): String =
        "<rss><channel><title>$sourceId</title>" +
            titles.joinToString("") { (slug, title) -> "<item><title>$title</title><link>https://$sourceId.example/news/$slug</link></item>" } +
            "</channel></rss>"

    @Test
    fun `the same story from several outlets becomes one card`() = runTest {
        web.responses[dw.feedUrl] = rssWithTitles("dw", "pope" to "Pope Leo leaves hospital after cancer surgery", "d2" to "Budget talks stall")
        web.responses[bbc.feedUrl] = rssWithTitles("bbc", "pope" to "Pope discharged from hospital days after lung cancer surgery")
        val viewModel = createViewModel()

        val state = viewModel.awaitState { it.stories.isNotEmpty() }

        assertEquals(2, state.stories.size)
        assertEquals("dw", state.stories[0].article.source.id)
        assertEquals(listOf("bbc"), state.stories[0].related.map { it.source.id })

        // A single outlet's view shows its own stories ungrouped.
        viewModel.selectSource("bbc")
        assertTrue(viewModel.awaitState { it.selectedSourceId == "bbc" }.stories.single().related.isEmpty())
    }

    @Test
    fun `stories read in the app are marked and can be hidden`() = runTest {
        val viewModel = createViewModel()
        val article = viewModel.awaitState { it.articles.isNotEmpty() }.articles.first()
        web.responses[article.item.url] = TestNews.articlePage()

        viewModel.readInApp(article.item)
        viewModel.awaitState { it.bookToOpen != null }
        viewModel.onReaderOpened()

        assertTrue(viewModel.awaitState { it.articles.first().isRead }.articles.first().isRead)
        viewModel.setHideRead(true)
        val hidden = viewModel.awaitState { it.hideRead }
        assertTrue(hidden.articles.none { it.item == article.item })
        assertEquals(2, hidden.articles.size)
    }

    @Test
    fun `stories opened online are marked read`() = runTest {
        val viewModel = createViewModel()
        val article = viewModel.awaitState { it.articles.isNotEmpty() }.articles.last()

        viewModel.onOpenedOnline(article.item)

        assertTrue(viewModel.awaitState { s -> s.articles.any { it.isRead } }.articles.last().isRead)
    }

    @Test
    fun `saved stories stay on the reading list and are downloaded for offline reading`() = runTest {
        val viewModel = createViewModel()
        val article = viewModel.awaitState { it.articles.isNotEmpty() }.articles.first()
        web.responses[article.item.url] = TestNews.articlePage()

        viewModel.setSaved(article.item, true)
        viewModel.awaitState { it.savedCount == 1 }
        viewModel.showSaved(true)

        val saved = viewModel.awaitState { it.showingSaved && it.stories.isNotEmpty() && it.stories[0].article.progress != null }
        assertEquals(listOf(article.item), saved.stories.map { it.article.item })
        assertTrue(saved.stories[0].article.isSaved)
        assertEquals(listOf(article.item.url), web.requests.filter { it == article.item.url })

        // Still there after the story leaves the feed.
        web.responses[dw.feedUrl] = TestNews.rss("dw", "other")
        viewModel.refresh()
        assertEquals(1, viewModel.awaitState { !it.isRefreshing }.stories.size)

        viewModel.setSaved(article.item, false)
        assertEquals(0, viewModel.awaitState { it.savedCount == 0 }.stories.size)
    }

    @Test
    fun `search filters headlines`() = runTest {
        val viewModel = createViewModel()
        viewModel.awaitState { it.articles.size == 3 }

        viewModel.setQuery("story b")

        val state = viewModel.awaitState { it.query == "story b" }
        assertEquals(listOf("b1", "b2"), state.articles.map { it.item.url.substringAfterLast('/') })
        assertTrue(state.isSearching)
    }

    @Test
    fun `half-read articles are offered to continue`() = runTest {
        val viewModel = createViewModel()
        val article = viewModel.awaitState { it.articles.isNotEmpty() }.articles.first()
        web.responses[article.item.url] = TestNews.articlePage()
        viewModel.readInApp(article.item)
        val bookId = viewModel.awaitState { it.bookToOpen != null }.bookToOpen!!
        viewModel.onReaderOpened()

        database.bookDao().updatePosition(bookId, chapterIndex = 0, wordIndex = 20, wordsRead = 20)

        val state = viewModel.awaitState { it.continueReading.isNotEmpty() }
        assertEquals(listOf(article.item), state.continueReading.map { it.item })
    }

    @Test
    fun `hiding an outlet from a card can be undone`() = runTest {
        val viewModel = createViewModel()
        viewModel.awaitState { it.articles.size == 3 }

        viewModel.hideSource(bbc)

        val hidden = viewModel.awaitState { s -> s.sources.none { it.id == "bbc" } }
        assertEquals(bbc, (hidden.message as NewsMessage.SourceHidden).source)
        assertTrue(hidden.articles.none { it.source.id == "bbc" })

        viewModel.undoHideSource(bbc)
        viewModel.awaitState { s -> s.sources.any { it.id == "bbc" } }
    }

    @Test
    fun `a manual refresh reports new stories`() = runTest {
        val viewModel = createViewModel()
        viewModel.awaitState { it.articles.size == 3 }
        web.responses[dw.feedUrl] = TestNews.rss("dw", "d1", "d2", "d3")

        viewModel.refresh()

        assertEquals(2, (viewModel.awaitState { it.message is NewsMessage.NewStories }.message as NewsMessage.NewStories).count)
    }

    @Test
    fun `the data saver hides images on metered networks or always`() = runTest {
        val viewModel = createViewModel()
        assertTrue(viewModel.awaitState { it.hasLoaded }.showImages)

        preferences.setNewsImages(NewsImages.UNMETERED_ONLY)
        assertTrue(viewModel.awaitState { s -> s.articles.isNotEmpty() }.showImages)
        metered.value = true
        viewModel.awaitState { !it.showImages }
        metered.value = false
        viewModel.awaitState { it.showImages }

        preferences.setNewsImages(NewsImages.NEVER)
        viewModel.awaitState { !it.showImages }
    }

    @Test
    fun `stories can be marked unread again`() = runTest {
        val viewModel = createViewModel()
        val article = viewModel.awaitState { it.articles.isNotEmpty() }.articles.first()

        viewModel.setRead(article.item, true)
        viewModel.awaitState { s -> s.articles.first().isRead }
        viewModel.setRead(article.item, false)
        viewModel.awaitState { s -> !s.articles.first().isRead }
    }
}
