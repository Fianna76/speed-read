package io.github.fianna76.speedread.ui.reader

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.fianna76.speedread.SpeedReadApplication
import io.github.fianna76.speedread.data.BookRepository
import io.github.fianna76.speedread.data.Chapter
import io.github.fianna76.speedread.data.ReadingPosition
import io.github.fianna76.speedread.data.settings.ReaderDisplay
import io.github.fianna76.speedread.data.settings.UserPreferencesRepository
import io.github.fianna76.speedread.rsvp.PlayerState
import io.github.fianna76.speedread.rsvp.RsvpPlayer
import io.github.fianna76.speedread.rsvp.WordsPerMinute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ReaderUiState(
    val isLoading: Boolean = true,
    val error: ReaderError? = null,
    val title: String = "",
    /** Web address of a news article, for "Open online"; `null` for books. */
    val sourceUrl: String? = null,
    /** Outlet of a news article (shown instead of the chapter title). */
    val sourceName: String? = null,
    val chapters: List<Chapter> = emptyList(),
    /** Chapter whose text is loaded in the player. */
    val chapterIndex: Int = 0,
    val totalWords: Int = 0,
    val isFinished: Boolean = false,
    /** Explains the tap zones; shown for unread books until playback first starts. */
    val showTapHint: Boolean = false,
    val display: ReaderDisplay = ReaderDisplay(),
) {
    val chapter: Chapter? get() = chapters.getOrNull(chapterIndex)
    val hasPreviousChapter: Boolean get() = chapterIndex > 0
    val hasNextChapter: Boolean get() = chapterIndex < chapters.lastIndex
}

enum class ReaderError { BOOK_NOT_FOUND, NO_TEXT, LOAD_FAILED }

/**
 * Coordinates the RSVP [RsvpPlayer] with the book's chapters: loads chapter text on demand,
 * continues into the next chapter automatically, and persists the reading position.
 *
 * Positions are written through [externalScope] so the final save survives this ViewModel
 * being cleared, and are serialised with a mutex so an older position can never overwrite a
 * newer one.
 */
class ReaderViewModel(
    private val bookId: Long,
    private val books: BookRepository,
    private val preferences: UserPreferencesRepository,
    private val externalScope: CoroutineScope,
    // The main thread's scheduling clock, so word deadlines line up with Dispatchers.Main delays.
    clock: () -> Long = SystemClock::uptimeMillis,
) : ViewModel() {

    private val player = RsvpPlayer(viewModelScope, clock, onEndReached = ::onEndOfChapter)
    val playerState: StateFlow<PlayerState> = player.state

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var chapterJob: Job? = null
    private var loadedChapterIndex = NO_CHAPTER
    private val saveMutex = Mutex()
    private var lastSaved: Pair<ReadingPosition, Int>? = null

    init {
        viewModelScope.launch {
            preferences.preferences.collect { prefs ->
                player.updateSettings(prefs.playback)
                _uiState.update { it.copy(display = prefs.display) }
            }
        }
        viewModelScope.launch {
            // Save whenever playback stops, and periodically while it runs (no timers while paused).
            player.state.map { it.isPlaying }.distinctUntilChanged().collectLatest { playing ->
                if (!playing) {
                    savePosition()
                    return@collectLatest
                }
                _uiState.update { it.copy(showTapHint = false) }
                while (true) {
                    delay(PERIODIC_SAVE_MS)
                    savePosition()
                }
            }
        }
        viewModelScope.launch { openBook() }
    }

    // --- User actions ------------------------------------------------------------------------

    fun togglePlayPause() {
        if (canControlPlayback()) player.togglePlayPause()
    }

    /** Next sentence; past the last sentence of a chapter, the next chapter. */
    fun skipForward() {
        if (!canControlPlayback()) return
        val wasPlaying = player.state.value.isPlaying
        if (!player.skipForward()) {
            if (_uiState.value.hasNextChapter) loadChapter(loadedChapterIndex + 1, autoPlay = wasPlaying) else finishBook()
        }
    }

    /** Start of the sentence (or the previous one); before the first, the end of the previous chapter. */
    fun skipBackward() {
        if (!canControlPlayback()) return
        val wasPlaying = player.state.value.isPlaying
        if (!player.skipBackward() && _uiState.value.hasPreviousChapter) {
            loadChapter(loadedChapterIndex - 1, autoPlay = wasPlaying, startAtLastSentence = true)
        }
    }

    fun nextChapter() {
        if (_uiState.value.hasNextChapter) {
            loadChapter(loadedChapterIndex + 1, autoPlay = player.state.value.isPlaying)
        }
    }

    /**
     * Like a music player's "previous" button: restarts the current chapter, or goes to the
     * previous one when still within the first sentence.
     */
    fun previousChapter() {
        val ui = _uiState.value
        if (ui.isLoading || loadedChapterIndex == NO_CHAPTER) return
        val state = player.state.value
        val withinFirstSentence = state.index < (state.text.nextSentenceStart(0) ?: state.text.size)
        when {
            withinFirstSentence && ui.hasPreviousChapter -> loadChapter(loadedChapterIndex - 1, autoPlay = state.isPlaying)
            ui.isFinished -> loadChapter(loadedChapterIndex, autoPlay = false)
            else -> player.seekTo(0)
        }
    }

    fun selectChapter(index: Int) = loadChapter(index, autoPlay = false)

    /** Live speed change while dragging the slider; call [commitWordsPerMinute] when done. */
    fun setWordsPerMinute(wpm: Int) {
        val settings = player.state.value.settings
        player.updateSettings(settings.copy(wordsPerMinute = WordsPerMinute.clamp(wpm)))
    }

    fun commitWordsPerMinute() {
        val wpm = player.state.value.settings.wordsPerMinute
        externalScope.launch { preferences.setWordsPerMinute(wpm) }
    }

    fun changeWordsPerMinuteBy(delta: Int) {
        setWordsPerMinute(player.state.value.settings.wordsPerMinute + delta)
        commitWordsPerMinute()
    }

    fun restartBook() = loadChapter(0, autoPlay = false)

    fun retry() {
        val ui = _uiState.value
        _uiState.update { it.copy(error = null, isLoading = true) }
        if (ui.chapters.isEmpty()) {
            viewModelScope.launch { openBook() }
        } else {
            loadChapter(ui.chapterIndex, autoPlay = false)
        }
    }

    /** The app went to the background: never keep flashing words nobody is looking at. */
    fun onBackgrounded() {
        player.pause()
        savePosition()
    }

    override fun onCleared() {
        // viewModelScope is already cancelled here; the save runs in externalScope.
        savePosition()
    }

    // --- Loading -----------------------------------------------------------------------------

    private suspend fun openBook() {
        val book = books.getBook(bookId)
        if (book == null) {
            _uiState.update { it.copy(isLoading = false, error = ReaderError.BOOK_NOT_FOUND) }
            return
        }
        val chapters = books.getChapters(bookId)
        if (chapters.isEmpty()) {
            _uiState.update { it.copy(isLoading = false, error = ReaderError.NO_TEXT, title = book.title) }
            return
        }
        _uiState.update {
            it.copy(
                title = book.title,
                sourceUrl = book.sourceUrl,
                sourceName = book.author.takeIf { book.sourceUrl != null },
                chapters = chapters,
                totalWords = book.totalWords,
                showTapHint = book.wordsRead == 0,
            )
        }
        books.markOpened(bookId)
        val chapterIndex = book.position.chapterIndex.coerceIn(0, chapters.lastIndex)
        val finished = book.totalWords > 0 && book.wordsRead >= book.totalWords
        loadChapter(chapterIndex, startWord = book.position.wordIndex, autoPlay = false, finished = finished)
    }

    private fun loadChapter(
        index: Int,
        startWord: Int = 0,
        autoPlay: Boolean,
        startAtLastSentence: Boolean = false,
        finished: Boolean = false,
    ) {
        val chapters = _uiState.value.chapters
        val chapter = chapters.getOrNull(index) ?: return
        savePosition() // Remember where we were in the chapter we're leaving.
        player.pause()
        chapterJob?.cancel()
        _uiState.update { it.copy(isLoading = true, isFinished = false, error = null) }
        chapterJob = viewModelScope.launch {
            val text = try {
                books.loadChapterText(bookId, chapter)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load chapter $index of book $bookId", e)
                _uiState.update { it.copy(isLoading = false, error = ReaderError.LOAD_FAILED, chapterIndex = index) }
                return@launch
            }
            if (text.isEmpty() && index < chapters.lastIndex) {
                // The section turned out to be empty (e.g. the extractor changed): move on.
                loadChapter(index + 1, autoPlay = autoPlay)
                return@launch
            }
            val start = if (startAtLastSentence) text.sentenceStartOf(text.size - 1) else startWord
            player.load(text, start)
            loadedChapterIndex = index
            _uiState.update { it.copy(isLoading = false, chapterIndex = index, isFinished = finished) }
            savePosition()
            if (autoPlay) player.play()
        }
    }

    private fun onEndOfChapter() {
        if (_uiState.value.hasNextChapter) loadChapter(loadedChapterIndex + 1, autoPlay = true) else finishBook()
    }

    private fun finishBook() {
        player.pause()
        _uiState.update { it.copy(isFinished = true) }
        savePosition()
    }

    private fun canControlPlayback(): Boolean {
        val ui = _uiState.value
        return !ui.isLoading && ui.error == null && !ui.isFinished && loadedChapterIndex != NO_CHAPTER
    }

    // --- Persistence -------------------------------------------------------------------------

    private fun savePosition() {
        val ui = _uiState.value
        val chapter = ui.chapters.getOrNull(loadedChapterIndex) ?: return
        val state = player.state.value
        if (state.text.isEmpty()) return
        val position = ReadingPosition(loadedChapterIndex, state.index)
        val wordsRead = if (ui.isFinished) ui.totalWords else chapter.startWord + state.index
        val snapshot = position to wordsRead
        if (snapshot == lastSaved) return
        lastSaved = snapshot
        // Main.immediate runs up to lock() synchronously, so writes queue up in call order.
        externalScope.launch(Dispatchers.Main.immediate) {
            saveMutex.withLock { books.savePosition(bookId, position, wordsRead) }
        }
    }

    companion object {
        private const val TAG = "ReaderViewModel"
        private const val NO_CHAPTER = -1
        private const val PERIODIC_SAVE_MS = 10_000L

        fun factory(bookId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as SpeedReadApplication).container
                ReaderViewModel(bookId, container.bookRepository, container.preferencesRepository, container.applicationScope)
            }
        }
    }
}
