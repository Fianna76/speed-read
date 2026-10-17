package io.github.fianna76.speedread.ui.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.speedread.data.Chapter
import io.github.fianna76.speedread.data.news.ArticleError
import io.github.fianna76.speedread.data.news.ArticleResult
import io.github.fianna76.speedread.data.news.UpNext
import io.github.fianna76.speedread.news.FeedItem
import io.github.fianna76.speedread.testing.FakeBookRepository
import io.github.fianna76.speedread.testing.MainDispatcherRule
import io.github.fianna76.speedread.testing.TestNews
import io.github.fianna76.speedread.testing.testBook
import io.github.fianna76.speedread.testing.testPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** "Next story" after a news article: offer, countdown, cancel, skipping unreadable stories. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ReaderUpNextTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val current = TestNews.item("rte", "current")
    private val next = TestNews.item("dw", "next", title = "The next story")
    private val after = TestNews.item("bbc", "after", title = "The one after")

    private class FakeUpNext(private val queue: List<FeedItem>, private val results: Map<String, ArticleResult>) : UpNext {
        val prefetched = mutableListOf<FeedItem>()
        val opened = mutableListOf<FeedItem>()

        override suspend fun nextAfter(url: String, skip: Set<String>): FeedItem? {
            val index = queue.indexOfFirst { it.canonicalUrl == url }
            return queue.drop(index + 1).firstOrNull { it.canonicalUrl !in skip }
        }

        override suspend fun open(item: FeedItem): ArticleResult = results.getValue(item.canonicalUrl).also { opened += item }

        override fun prefetch(item: FeedItem) {
            prefetched += item
        }
    }

    private lateinit var upNext: FakeUpNext

    private suspend fun TestScope.createViewModel(
        autoContinue: Boolean = true,
        results: Map<String, ArticleResult> = mapOf(next.canonicalUrl to ArticleResult.Ready(2)),
        autoPlay: Boolean = false,
    ): ReaderViewModel {
        val repository = FakeBookRepository(
            books = listOf(testBook(1, title = "Current", totalWords = 4).copy(sourceUrl = current.canonicalUrl)),
            chapters = mapOf(1L to listOf(Chapter(0, null, "article", 4, 0))),
            texts = mapOf((1L to 0) to listOf("One two. Three four.")),
        )
        val preferences = testPreferences(tmp.newFolder(), backgroundScope).apply {
            setWordsPerMinute(600)
            setSmartPacing(false)
            setRampUp(false)
            setAutoContinueNews(autoContinue)
        }
        upNext = FakeUpNext(listOf(current, next, after), results)
        val viewModel = ReaderViewModel(
            1, repository, preferences, backgroundScope, clock = { testScheduler.currentTime }, upNext = upNext, autoPlay = autoPlay,
        )
        viewModel.uiState.first { !it.isLoading && it.upNext != null }
        return viewModel
    }

    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun `the next story is offered and downloaded ahead of time`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        assertEquals(UpNextStory(next, "DW"), viewModel.uiState.value.upNext)
        assertEquals(listOf(next), upNext.prefetched)
    }

    @Test
    fun `finishing an article counts down, then opens the next story`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.togglePlayPause()
        advance(1_000) // Four words at 100 ms each, then the end.
        assertTrue(viewModel.uiState.value.isFinished)
        assertEquals(ReaderViewModel.AUTO_CONTINUE_SECONDS, viewModel.uiState.value.autoContinueIn)

        advance(ReaderViewModel.AUTO_CONTINUE_SECONDS * 1_000L)

        assertEquals(2L, viewModel.uiState.value.nextBookToOpen)
        assertEquals(listOf(next), upNext.opened)
        viewModel.onNextOpened()
        assertNull(viewModel.uiState.value.nextBookToOpen)
    }

    @Test
    fun `the countdown can be cancelled and the next story opened by hand`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        viewModel.togglePlayPause()
        advance(1_000)

        viewModel.cancelAutoContinue()
        advance(10_000)
        assertNull(viewModel.uiState.value.autoContinueIn)
        assertNull(viewModel.uiState.value.nextBookToOpen)

        viewModel.readNext()
        advance(0)
        assertEquals(2L, viewModel.uiState.value.nextBookToOpen)
    }

    @Test
    fun `without auto-continue the next story waits for a tap`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel(autoContinue = false)
        viewModel.togglePlayPause()
        advance(10_000)

        assertTrue(viewModel.uiState.value.isFinished)
        assertNull(viewModel.uiState.value.autoContinueIn)
        assertNull(viewModel.uiState.value.nextBookToOpen)
    }

    @Test
    fun `a story that cannot be read is skipped for the one after`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel(
            results = mapOf(next.canonicalUrl to ArticleResult.Failed(ArticleError.PAYWALLED), after.canonicalUrl to ArticleResult.Ready(3)),
        )
        viewModel.togglePlayPause()
        advance(1_000 + ReaderViewModel.AUTO_CONTINUE_SECONDS * 1_000L)

        assertEquals(UpNextStory(after, "BBC News"), viewModel.uiState.value.upNext)
        advance(ReaderViewModel.AUTO_CONTINUE_SECONDS * 1_000L)
        assertEquals(3L, viewModel.uiState.value.nextBookToOpen)
    }

    @Test
    fun `a story opened from the previous one starts playing`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel(autoPlay = true)

        assertTrue(viewModel.playerState.value.isPlaying)
    }
}
