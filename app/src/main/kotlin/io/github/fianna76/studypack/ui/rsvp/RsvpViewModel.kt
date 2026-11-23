package io.github.fianna76.studypack.ui.rsvp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.fianna76.speedread.rsvp.PlaybackSettings
import io.github.fianna76.speedread.rsvp.PlayerState
import io.github.fianna76.speedread.rsvp.RsvpPlayer
import io.github.fianna76.speedread.rsvp.WordsPerMinute
import io.github.fianna76.studypack.StudyPackApplication
import io.github.fianna76.studypack.data.StudyRepository
import io.github.fianna76.studypack.data.pdf.NotesBook
import io.github.fianna76.studypack.data.pdf.NotesPosition
import io.github.fianna76.studypack.data.pdf.NotesTextLoader
import io.github.fianna76.studypack.data.pdf.SectionKind
import io.github.fianna76.studypack.data.settings.ReadingPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RsvpProblem { NO_TEXT, FAILED }

/** A section as listed in the sections sheet. */
data class ChapterInfo(
    val title: String?,
    val kind: SectionKind,
    val wordCount: Int,
    val firstPage: Int,
    val lastPage: Int,
)

data class RsvpUiState(
    val isLoading: Boolean = true,
    val problem: RsvpProblem? = null,
    val title: String = "",
    val chapters: List<ChapterInfo> = emptyList(),
    val chapterIndex: Int = 0,
    /** The end of the notes was reached (back matter is not read on into). */
    val isFinished: Boolean = false,
    /** Shown until the first play. */
    val showTapHint: Boolean = true,
) {
    val chapter: ChapterInfo? get() = chapters.getOrNull(chapterIndex)

    val canControl: Boolean get() = !isLoading && problem == null && !isFinished

    val hasPreviousChapter: Boolean get() = chapterIndex > 0

    val hasNextChapter: Boolean get() = chapterIndex < chapters.lastIndex

    /** Words in the notes themselves (front and back matter left out), for progress and time left. */
    val bodyWords: Int get() = chapters.filter { it.kind == SectionKind.BODY }.sumOf { it.wordCount }

    /** Body words before chapter [index]. */
    fun bodyWordsBefore(index: Int): Int = chapters.take(index).filter { it.kind == SectionKind.BODY }.sumOf { it.wordCount }
}

/**
 * Speed reading for notes PDFs, by section: reading starts at the first section (after the title
 * page and contents), runs from section to section, and stops before the sources. Any section,
 * including the skipped ones, can be picked from the sections sheet. The position is saved as the
 * exact word, and as a page for the notes viewer.
 */
class RsvpViewModel(
    private val noteId: Long,
    /** Start at this page instead of where reading last stopped. */
    private val startPage: Int?,
    private val repository: StudyRepository,
    private val loader: NotesTextLoader,
    private val preferences: ReadingPreferences,
    private val writeScope: CoroutineScope,
) : ViewModel() {

    private val player = RsvpPlayer(viewModelScope, onEndReached = ::onChapterEnd)
    val playerState: StateFlow<PlayerState> = player.state

    private val _uiState = MutableStateFlow(RsvpUiState())
    val uiState: StateFlow<RsvpUiState> = _uiState.asStateFlow()

    private var book: NotesBook? = null

    /** PDF page of the word on screen, for the controls. */
    val currentPage: StateFlow<Int> = combine(_uiState, player.state) { ui, state ->
        book?.chapters?.getOrNull(ui.chapterIndex)?.pageOf(state.index) ?: 0
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        player.updateSettings(PlaybackSettings(wordsPerMinute = preferences.settings.first().wordsPerMinute))
        val note = repository.note(noteId)
        if (note == null) {
            _uiState.update { it.copy(isLoading = false, problem = RsvpProblem.FAILED) }
            return
        }
        val loaded = runCatching { withContext(Dispatchers.IO) { loader.load(note.internalPath) } }.getOrNull()
        if (loaded == null || loaded.totalWords == 0) {
            _uiState.update {
                it.copy(isLoading = false, title = note.displayTitle, problem = if (loaded == null) RsvpProblem.FAILED else RsvpProblem.NO_TEXT)
            }
            return
        }
        book = loaded
        val position = when {
            startPage != null -> loaded.positionForPage(startPage)
            note.rsvpWord >= 0 -> loaded.locate(note.rsvpWord) ?: loaded.start
            else -> loaded.positionForPage(note.lastPage)
        }
        // Load the text first and publish everything at once, so the screen never shows the
        // title page for a moment before jumping to where reading starts.
        player.load(loaded.chapters[position.chapter].text, position.index)
        _uiState.update {
            it.copy(
                isLoading = false,
                title = note.displayTitle,
                chapters = loaded.chapters.map { c -> ChapterInfo(c.title, c.kind, c.wordCount, c.firstPage, c.lastPage) },
                chapterIndex = position.chapter,
            )
        }
    }

    private fun show(position: NotesPosition, play: Boolean = false) {
        val book = book ?: return
        val chapter = book.chapters.getOrNull(position.chapter) ?: return
        player.load(chapter.text, position.index)
        _uiState.update { it.copy(chapterIndex = position.chapter, isFinished = false) }
        if (play) play()
    }

    /** At the end of a section during playback: on to the next section of the notes, or the end. */
    private fun onChapterEnd() {
        val book = book ?: return
        val next = book.nextChapterAfter(_uiState.value.chapterIndex)
        if (next != null) {
            show(NotesPosition(next, 0), play = true)
        } else {
            _uiState.update { it.copy(isFinished = true) }
            save()
        }
    }

    private fun play() {
        _uiState.update { it.copy(showTapHint = false) }
        player.play()
    }

    fun togglePlayPause() {
        if (!_uiState.value.canControl) return
        if (player.state.value.isPlaying) pause() else play()
    }

    /** Pauses and saves the position. */
    fun pause() {
        player.pause()
        save()
    }

    fun previousSentence() {
        if (!player.skipBackward()) {
            // At the very start of a section: back into the end of the previous one.
            val previous = _uiState.value.chapterIndex - 1
            val book = book ?: return
            if (previous >= 0) {
                val wasPlaying = player.state.value.isPlaying
                val text = book.chapters[previous].text
                show(NotesPosition(previous, text.sentenceStartOf(text.size - 1)), play = wasPlaying)
            }
        }
    }

    fun nextSentence() {
        if (!player.skipForward()) {
            val next = _uiState.value.chapterIndex + 1
            if (next < (book?.chapters?.size ?: 0)) show(NotesPosition(next, 0), play = player.state.value.isPlaying)
        }
    }

    /** The start of this section, or the previous section when already near its start. */
    fun previousChapter() {
        val ui = _uiState.value
        val wasPlaying = player.state.value.isPlaying
        val target = if (player.state.value.index > RESTART_THRESHOLD_WORDS || ui.chapterIndex == 0) ui.chapterIndex else ui.chapterIndex - 1
        show(NotesPosition(target, 0), play = wasPlaying)
        save()
    }

    fun nextChapter() {
        val ui = _uiState.value
        if (!ui.hasNextChapter) return
        show(NotesPosition(ui.chapterIndex + 1, 0), play = player.state.value.isPlaying)
        save()
    }

    fun selectChapter(index: Int) {
        player.pause()
        show(NotesPosition(index, 0))
        save()
    }

    /** Dragging the position slider: paused, the word on screen previews where reading continues. */
    fun scrubTo(fraction: Float) {
        val size = player.state.value.text.size
        if (size == 0) return
        player.pause()
        player.seekTo(((size - 1) * fraction.coerceIn(0f, 1f)).toInt())
    }

    fun scrubFinished() = save()

    /** A word tapped in the page view: continue from it. */
    fun seekToWord(index: Int) {
        player.pause()
        player.seekTo(index)
        save()
    }

    /** Live while the speed slider is dragged; saved by [commitWordsPerMinute]. */
    fun setWordsPerMinute(wpm: Int) {
        val clamped = WordsPerMinute.clamp((wpm / WordsPerMinute.STEP.toFloat()).let { Math.round(it) } * WordsPerMinute.STEP)
        player.updateSettings(player.state.value.settings.copy(wordsPerMinute = clamped))
    }

    fun commitWordsPerMinute() {
        val wpm = player.state.value.settings.wordsPerMinute
        writeScope.launch { preferences.setWordsPerMinute(wpm) }
    }

    fun changeWordsPerMinuteBy(delta: Int) {
        setWordsPerMinute(player.state.value.settings.wordsPerMinute + delta)
        commitWordsPerMinute()
    }

    /** From the end screen: the notes again from the first section. */
    fun restart() {
        val book = book ?: return
        show(book.start)
        save()
    }

    private fun save() {
        val book = book ?: return
        val ui = _uiState.value
        val chapter = book.chapters.getOrNull(ui.chapterIndex) ?: return
        val index = player.state.value.index
        // Finished notes start again from the first section next time.
        val word = if (ui.isFinished) book.absolute(book.start) else book.absolute(NotesPosition(ui.chapterIndex, index))
        val page = chapter.pageOf(index)
        writeScope.launch { repository.updateReadingPosition(noteId, page, word) }
    }

    override fun onCleared() {
        player.pause()
        save()
    }

    companion object {
        /** "Previous section" restarts the current one once this far in. */
        private const val RESTART_THRESHOLD_WORDS = 20

        fun factory(noteId: Long, startPage: Int?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as StudyPackApplication).container
                RsvpViewModel(
                    noteId,
                    startPage,
                    container.repository,
                    NotesTextLoader(container.files, container.pdfDocuments),
                    container.readingPreferences,
                    container.applicationScope,
                )
            }
        }
    }
}
