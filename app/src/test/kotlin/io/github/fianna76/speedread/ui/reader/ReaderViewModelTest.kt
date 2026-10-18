package io.github.fianna76.speedread.ui.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.speedread.data.Chapter
import io.github.fianna76.speedread.data.ReadingPosition
import io.github.fianna76.speedread.data.settings.UserPreferencesRepository
import io.github.fianna76.speedread.rsvp.PlaybackSettings
import io.github.fianna76.speedread.testing.FakeBookRepository
import io.github.fianna76.speedread.testing.MainDispatcherRule
import io.github.fianna76.speedread.testing.testBook
import io.github.fianna76.speedread.testing.testPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ReaderViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tmp = TemporaryFolder()

    // Chapter 0: One0 two.1 | Three2 four.3     Chapter 1: Five0 six.1 | Seven2 eight.3
    private val chapters = listOf(
        Chapter(index = 0, title = "First", href = "c0.xhtml", wordCount = 4, startWord = 0),
        Chapter(index = 1, title = "Second", href = "c1.xhtml", wordCount = 4, startWord = 4),
    )
    private val texts = mapOf(
        (BOOK_ID to 0) to listOf("One two. Three four."),
        (BOOK_ID to 1) to listOf("Five six. Seven eight."),
    )

    private lateinit var repository: FakeBookRepository
    private lateinit var preferences: UserPreferencesRepository

    /** Creates the ViewModel at 600 wpm without pacing (exactly 100 ms per word) and waits until it's ready. */
    private suspend fun TestScope.createViewModel(
        position: ReadingPosition = ReadingPosition(0, 0),
        wordsRead: Int = 0,
    ): ReaderViewModel {
        repository = FakeBookRepository(
            books = listOf(testBook(BOOK_ID, title = "Test", totalWords = 8, position = position, wordsRead = wordsRead)),
            chapters = mapOf(BOOK_ID to chapters),
            texts = texts,
        )
        preferences = testPreferences(tmp.newFolder(), backgroundScope)
        preferences.setWordsPerMinute(600)
        preferences.setSmartPacing(false)
        preferences.setRampUp(false)
        return newViewModel().also { awaitReady(it) }
    }

    private fun TestScope.newViewModel() =
        ReaderViewModel(BOOK_ID, repository, preferences, backgroundScope, clock = { testScheduler.currentTime })

    private suspend fun awaitReady(viewModel: ReaderViewModel) {
        viewModel.playerState.first { it.settings == FLAT }
        viewModel.uiState.first { !it.isLoading || it.error != null }
    }

    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun `opens the book at the saved position`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel(position = ReadingPosition(chapterIndex = 1, wordIndex = 2), wordsRead = 6)

        val ui = viewModel.uiState.value
        assertFalse(ui.isLoading)
        assertEquals("Test", ui.title)
        assertEquals(1, ui.chapterIndex)
        assertEquals("Seven", viewModel.playerState.value.word?.text)
        assertEquals(listOf(BOOK_ID), repository.openedBooks)
        assertFalse(ui.showTapHint) // Already reading this book.
    }

    @Test
    fun `bookmarks toggle at the current word and take you back to it`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel(position = ReadingPosition(1, 2), wordsRead = 6)

        viewModel.toggleBookmark()
        val marked = viewModel.uiState.first { it.bookmarks.isNotEmpty() }.bookmarks.single()
        assertEquals(ReadingPosition(1, 2), marked.position)
        assertEquals("Seven eight.", marked.snippet)
        assertTrue(viewModel.uiState.value.bookmarkAt(2) != null)

        viewModel.selectChapter(0)
        viewModel.uiState.first { it.chapterIndex == 0 && !it.isLoading }
        viewModel.goToBookmark(marked)
        val state = viewModel.uiState.first { it.chapterIndex == 1 && !it.isLoading }
        assertEquals(2, viewModel.playerState.value.index)

        viewModel.toggleBookmark() // Same word: removes it.
        assertTrue(viewModel.uiState.first { it.bookmarks.isEmpty() }.bookmarks.isEmpty())
        assertNull(state.bookmarkAt(3))
    }

    @Test
    fun `speed training steps the speed up every two minutes of reading, up to the target`() =
        runTest(mainDispatcherRule.dispatcher) {
            val long = List(3_000) { "word" }.joinToString(" ")
            repository = FakeBookRepository(
                books = listOf(testBook(BOOK_ID, totalWords = 3_000)),
                chapters = mapOf(BOOK_ID to listOf(Chapter(0, null, "c0.xhtml", 3_000, 0))),
                texts = mapOf((BOOK_ID to 0) to listOf(long)),
            )
            preferences = testPreferences(tmp.newFolder(), backgroundScope)
            preferences.setWordsPerMinute(600)
            preferences.setSmartPacing(false)
            preferences.setRampUp(false)
            preferences.setSpeedTraining(true)
            preferences.setTrainingTarget(650)
            val viewModel = newViewModel().also { awaitReady(it) }
            assertEquals(650, viewModel.uiState.first { it.trainingTarget != null }.trainingTarget)

            viewModel.togglePlayPause()
            advance(60_000)
            viewModel.togglePlayPause() // Paused time doesn't count.
            advance(600_000)
            assertEquals(600, viewModel.playerState.value.settings.wordsPerMinute)
            viewModel.togglePlayPause()
            advance(60_000)
            assertEquals(625, viewModel.playerState.value.settings.wordsPerMinute)
            advance(120_000)
            assertEquals(650, viewModel.playerState.value.settings.wordsPerMinute)
            assertEquals(650, preferences.preferences.first().playback.wordsPerMinute)
            assertNull(viewModel.uiState.first { it.trainingTarget == null }.trainingTarget) // Reached.
            advance(120_000)
            assertEquals(650, viewModel.playerState.value.settings.wordsPerMinute)
        }

    @Test
    fun `reading time is recorded for the statistics`() = runTest(mainDispatcherRule.dispatcher) {
        val database = androidx.room.Room.inMemoryDatabaseBuilder(
            androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            io.github.fianna76.speedread.data.db.SpeedReadDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            createViewModel()
            val stats = io.github.fianna76.speedread.data.stats.ReadingStats(database.readingDayDao())
            val viewModel = ReaderViewModel(
                BOOK_ID, repository, preferences, backgroundScope, clock = { testScheduler.currentTime }, stats = stats,
            ).also { awaitReady(it) }

            viewModel.togglePlayPause()
            advance(250)
            viewModel.togglePlayPause()
            runCurrent()

            val summary = stats.observe().first { it.totalWords > 0 }
            assertEquals(2, summary.totalWords) // Two words passed in 250 ms at 600 wpm.
            assertEquals(250, summary.totalMillis)
        } finally {
            database.close()
        }
    }

    @Test
    fun `scrubbing pauses and moves through the chapter`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        viewModel.togglePlayPause()
        advance(50)

        viewModel.scrubTo(1f)
        assertFalse(viewModel.playerState.value.isPlaying)
        assertEquals(3, viewModel.playerState.value.index)
        viewModel.scrubTo(0.4f)
        assertEquals(1, viewModel.playerState.value.index)
    }

    @Test
    fun `plays across a chapter boundary without stopping`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value.showTapHint) // Unread book.

        viewModel.togglePlayPause()
        assertTrue(viewModel.playerState.value.isPlaying)
        assertFalse(viewModel.uiState.value.showTapHint)
        advance(400) // the four words of chapter 0
        assertEquals(1, viewModel.uiState.value.chapterIndex)
        assertTrue(viewModel.playerState.value.isPlaying)
        assertEquals("Five", viewModel.playerState.value.word?.text)
        advance(100)
        assertEquals("six.", viewModel.playerState.value.word?.text)

        viewModel.togglePlayPause()
        runCurrent()
        assertEquals(ReadingPosition(1, 1), repository.savedPositions.last().position)
        assertEquals(5, repository.savedPositions.last().wordsRead)
    }

    @Test
    fun `skipping past the last sentence moves to the next chapter`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel(position = ReadingPosition(0, 2))

        viewModel.skipForward()
        runCurrent()
        assertEquals(1, viewModel.uiState.value.chapterIndex)
        assertEquals("Five", viewModel.playerState.value.word?.text)
        assertFalse(viewModel.playerState.value.isPlaying)
    }

    @Test
    fun `skipping back from a chapter start lands on the previous chapter's last sentence`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel(position = ReadingPosition(1, 0), wordsRead = 4)

            viewModel.skipBackward()
            runCurrent()
            assertEquals(0, viewModel.uiState.value.chapterIndex)
            assertEquals("Three", viewModel.playerState.value.word?.text)
        }

    @Test
    fun `previous chapter restarts the chapter before going back`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel(position = ReadingPosition(1, 3), wordsRead = 7)

        viewModel.previousChapter()
        runCurrent()
        assertEquals(1, viewModel.uiState.value.chapterIndex)
        assertEquals(0, viewModel.playerState.value.index)

        viewModel.previousChapter()
        runCurrent()
        assertEquals(0, viewModel.uiState.value.chapterIndex)
    }

    @Test
    fun `finishing the book records full progress`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel(position = ReadingPosition(1, 2), wordsRead = 6)

        viewModel.togglePlayPause()
        advance(200)
        assertTrue(viewModel.uiState.value.isFinished)
        assertFalse(viewModel.playerState.value.isPlaying)
        assertEquals(8, repository.savedPositions.last().wordsRead)

        viewModel.restartBook()
        runCurrent()
        assertFalse(viewModel.uiState.value.isFinished)
        assertEquals(0, viewModel.uiState.value.chapterIndex)
        assertEquals(0, viewModel.playerState.value.index)
    }

    @Test
    fun `speed changes apply immediately and persist when committed`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.setWordsPerMinute(450)
        assertEquals(450, viewModel.playerState.value.settings.wordsPerMinute)
        viewModel.commitWordsPerMinute()
        preferences.preferences.first { it.playback.wordsPerMinute == 450 }

        viewModel.changeWordsPerMinuteBy(25)
        assertEquals(475, viewModel.playerState.value.settings.wordsPerMinute)
        preferences.preferences.first { it.playback.wordsPerMinute == 475 }
        viewModel.playerState.first { it.settings.wordsPerMinute == 475 }

        viewModel.setWordsPerMinute(5_000)
        assertEquals(1_000, viewModel.playerState.value.settings.wordsPerMinute)
    }

    @Test
    fun `backgrounding pauses and saves`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.togglePlayPause()
        advance(250)
        viewModel.onBackgrounded()
        runCurrent()
        assertFalse(viewModel.playerState.value.isPlaying)
        assertEquals(ReadingPosition(0, 2), repository.savedPositions.last().position)
    }

    @Test
    fun `a missing book shows an error`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        repository.books.value = emptyList()
        val missing = newViewModel().also { awaitReady(it) }

        assertEquals(ReaderError.BOOK_NOT_FOUND, missing.uiState.value.error)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `a chapter that fails to load can be retried`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        repository.failChapterLoads = true
        viewModel.selectChapter(1)
        runCurrent()
        assertEquals(ReaderError.LOAD_FAILED, viewModel.uiState.value.error)

        repository.failChapterLoads = false
        viewModel.retry()
        runCurrent()
        assertNull(viewModel.uiState.value.error)
        assertEquals(1, viewModel.uiState.value.chapterIndex)
        assertEquals("Five", viewModel.playerState.value.word?.text)
    }

    private companion object {
        const val BOOK_ID = 7L
        val FLAT = PlaybackSettings(wordsPerMinute = 600, smartPacing = false, rampUp = false)
    }
}
