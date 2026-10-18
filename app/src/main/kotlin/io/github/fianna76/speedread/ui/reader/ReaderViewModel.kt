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
import io.github.fianna76.speedread.data.Bookmark
import io.github.fianna76.speedread.data.Chapter
import io.github.fianna76.speedread.data.ReadingPosition
import io.github.fianna76.speedread.data.settings.ReaderDisplay
import io.github.fianna76.speedread.data.news.ArticleResult
import io.github.fianna76.speedread.data.news.UpNext
import io.github.fianna76.speedread.data.settings.UserPreferences
import io.github.fianna76.speedread.data.settings.UserPreferencesRepository
import io.github.fianna76.speedread.news.FeedItem
import io.github.fianna76.speedread.news.NewsSources
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
import kotlin.math.roundToInt

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
    /** For news articles: the next unread story from the list the article was opened from. */
    val upNext: UpNextStory? = null,
    /** Seconds until the next story starts by itself; `null` when no countdown is running. */
    val autoContinueIn: Int? = null,
    /** The next story is being downloaded. */
    val preparingNext: Boolean = false,
    /** Next story ready to open; the UI navigates and calls [ReaderViewModel.onNextOpened]. */
    val nextBookToOpen: Long? = null,
    /** Saved places in this book, in reading order. */
    val bookmarks: List<Bookmark> = emptyList(),
    /** Volume keys move between sentences (keyboards and page turners always work). */
    val volumeKeysTurnSentences: Boolean = false,
    /** Speed training's target while it is still raising the speed; `null` when off or reached. */
    val trainingTarget: Int? = null,
) {
    val chapter: Chapter? get() = chapters.getOrNull(chapterIndex)
    val hasPreviousChapter: Boolean get() = chapterIndex > 0
    val hasNextChapter: Boolean get() = chapterIndex < chapters.lastIndex

    /** The bookmark at [wordIndex] of the current chapter, if there is one. */
    fun bookmarkAt(wordIndex: Int): Bookmark? =
        bookmarks.firstOrNull { it.position.chapterIndex == chapterIndex && it.position.wordIndex == wordIndex }
}

enum class ReaderError { BOOK_NOT_FOUND, NO_TEXT, LOAD_FAILED }

/** The story offered after a news article. */
data class UpNextStory(val item: FeedItem, val sourceName: String?)

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
    /** Offers the next news story after an article; `null` disables it. */
    private val upNext: UpNext? = null,
    /** Start playing as soon as the text is loaded (continuing from the previous story). */
    private val autoPlay: Boolean = false,
) : ViewModel() {

    private val player = RsvpPlayer(viewModelScope, clock, onEndReached = ::onEndOfChapter)
    val playerState: StateFlow<PlayerState> = player.state

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var chapterJob: Job? = null
    private var loadedChapterIndex = NO_CHAPTER
    private val saveMutex = Mutex()
    private var lastSaved: Pair<ReadingPosition, Int>? = null
    private var autoContinue = true
    private var training: Int? = null
    private var countdownJob: Job? = null
    /** Next stories that could not be read (no text, paywall, offline), skipped from now on. */
    private val skippedNext = HashSet<String>()

    init {
        viewModelScope.launch {
            preferences.preferences.collect { prefs ->
                player.updateSettings(prefs.playback)
                autoContinue = prefs.autoContinueNews
                training = prefs.trainingTargetWpm.takeIf { prefs.speedTraining && prefs.playback.wordsPerMinute < it }
                _uiState.update { it.copy(trainingTarget = training) }
                _uiState.update { it.copy(display = prefs.display, volumeKeysTurnSentences = prefs.volumeKeysTurnSentences) }
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
        viewModelScope.launch {
            // Speed training: count reading time (not paused time) and step the speed up.
            var readMillis = 0L
            player.state.map { it.isPlaying }.distinctUntilChanged().collectLatest { playing ->
                while (playing) {
                    delay(TRAINING_TICK_MS)
                    if (training == null) continue
                    readMillis += TRAINING_TICK_MS
                    if (readMillis >= TRAINING_INTERVAL_MS) {
                        readMillis = 0
                        stepUpSpeed()
                    }
                }
            }
        }
        viewModelScope.launch {
            books.observeBookmarks(bookId).collect { marks -> _uiState.update { it.copy(bookmarks = marks) } }
        }
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

    /**
     * Moves to [fraction] of the current chapter while the scrubber is dragged. Playback
     * pauses, so the word shown (and the paused context) previews the new position.
     */
    fun scrubTo(fraction: Float) {
        if (!canControlPlayback()) return
        val size = player.state.value.text.size
        if (size == 0) return
        player.pause()
        player.seekTo(((size - 1) * fraction.coerceIn(0f, 1f)).roundToInt())
    }

    /** Bookmarks the current word, or removes the bookmark already there. */
    fun toggleBookmark() {
        if (!canControlPlayback()) return
        val state = player.state.value
        val existing = _uiState.value.bookmarkAt(state.index)
        val position = ReadingPosition(loadedChapterIndex, state.index)
        val snippet = state.text.words.drop(state.index).take(BOOKMARK_SNIPPET_WORDS).joinToString(" ") { it.text }
        externalScope.launch {
            if (existing != null) books.deleteBookmark(existing.id) else books.addBookmark(bookId, position, snippet)
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        externalScope.launch { books.deleteBookmark(bookmark.id) }
    }

    fun goToBookmark(bookmark: Bookmark) {
        val position = bookmark.position
        if (position.chapterIndex == loadedChapterIndex && !_uiState.value.isFinished && !_uiState.value.isLoading) {
            player.pause()
            player.seekTo(position.wordIndex)
        } else {
            loadChapter(position.chapterIndex, startWord = position.wordIndex, autoPlay = false)
        }
    }

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

    private fun stepUpSpeed() {
        val target = training ?: return
        val wpm = player.state.value.settings.wordsPerMinute
        if (wpm >= target) return
        setWordsPerMinute(minOf(wpm + UserPreferences.SPEED_TRAINING_STEP, target))
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
        loadChapter(chapterIndex, startWord = book.position.wordIndex, autoPlay = autoPlay && !finished, finished = finished)
        book.sourceUrl?.let { url -> findUpNext(url, prefetch = true) }
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
        if (autoContinue && _uiState.value.upNext != null) startCountdown()
    }

    // --- Next story (news articles) ------------------------------------------------------------

    /** Opens the next story now (also what the countdown does when it reaches zero). */
    fun readNext() {
        val next = _uiState.value.upNext ?: return
        val upNext = upNext ?: return
        countdownJob?.cancel()
        _uiState.update { it.copy(autoContinueIn = null, preparingNext = true) }
        viewModelScope.launch {
            when (val result = upNext.open(next.item)) {
                is ArticleResult.Ready -> _uiState.update { it.copy(preparingNext = false, nextBookToOpen = result.bookId) }
                is ArticleResult.Failed -> {
                    // Skip stories that can't be read here and offer the one after.
                    skippedNext += next.item.canonicalUrl
                    _uiState.update { it.copy(preparingNext = false) }
                    _uiState.value.sourceUrl?.let { url -> findUpNext(url, prefetch = false) }
                    if (_uiState.value.upNext != null && autoContinue) startCountdown()
                }
            }
        }
    }

    fun cancelAutoContinue() {
        countdownJob?.cancel()
        _uiState.update { it.copy(autoContinueIn = null) }
    }

    fun onNextOpened() = _uiState.update { it.copy(nextBookToOpen = null) }

    private suspend fun findUpNext(url: String, prefetch: Boolean) {
        val upNext = upNext ?: return
        val next = upNext.nextAfter(url, skippedNext)
        _uiState.update { state -> state.copy(upNext = next?.let { UpNextStory(it, NewsSources.byId(it.sourceId)?.name) }) }
        if (next != null && prefetch) upNext.prefetch(next)
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (seconds in AUTO_CONTINUE_SECONDS downTo 1) {
                _uiState.update { it.copy(autoContinueIn = seconds) }
                delay(1_000)
            }
            readNext()
        }
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
        private const val BOOKMARK_SNIPPET_WORDS = 12
        private const val TRAINING_TICK_MS = 1_000L
        private const val TRAINING_INTERVAL_MS = UserPreferences.SPEED_TRAINING_INTERVAL_MINUTES * 60_000L
        const val AUTO_CONTINUE_SECONDS = 5

        fun factory(bookId: Long, autoPlay: Boolean = false): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as SpeedReadApplication).container
                ReaderViewModel(
                    bookId,
                    container.bookRepository,
                    container.preferencesRepository,
                    container.applicationScope,
                    upNext = container.newsUpNext,
                    autoPlay = autoPlay,
                )
            }
        }
    }
}
