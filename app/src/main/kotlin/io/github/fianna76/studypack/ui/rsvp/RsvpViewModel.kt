package io.github.fianna76.studypack.ui.rsvp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.fianna76.speedread.rsvp.ContextWindow
import io.github.fianna76.speedread.rsvp.PlaybackSettings
import io.github.fianna76.speedread.rsvp.RsvpPlayer
import io.github.fianna76.speedread.rsvp.RsvpWord
import io.github.fianna76.speedread.rsvp.WordsPerMinute
import io.github.fianna76.studypack.StudyPackApplication
import io.github.fianna76.studypack.data.StudyRepository
import io.github.fianna76.studypack.data.pdf.NotesText
import io.github.fianna76.studypack.data.pdf.NotesTextLoader
import io.github.fianna76.studypack.data.settings.ReadingPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RsvpProblem { NO_TEXT, FAILED }

data class RsvpUiState(
    val isLoading: Boolean = true,
    val problem: RsvpProblem? = null,
    val title: String = "",
    val word: RsvpWord? = null,
    val isPlaying: Boolean = false,
    val wordsPerMinute: Int = WordsPerMinute.DEFAULT,
    val progress: Float = 0f,
    val page: Int = 0,
    val pageCount: Int = 0,
    /** The words around the current one, shown while paused. */
    val context: ContextWindow? = null,
)

private data class Loaded(val title: String, val notes: NotesText?, val problem: RsvpProblem?)

/**
 * Speed reading for notes PDFs (the app's secondary tool): flashes the notes' text one word at
 * a time from the chosen page, and leaves the viewer on the page reached.
 */
class RsvpViewModel(
    private val noteId: Long,
    private val startPage: Int,
    private val repository: StudyRepository,
    private val loader: NotesTextLoader,
    private val preferences: ReadingPreferences,
    private val writeScope: CoroutineScope,
) : ViewModel() {

    private val player = RsvpPlayer(viewModelScope)
    private val loaded = MutableStateFlow<Loaded?>(null)

    val uiState: StateFlow<RsvpUiState> = combine(loaded, player.state) { loaded, player ->
        when {
            loaded == null -> RsvpUiState()
            loaded.notes == null -> RsvpUiState(isLoading = false, problem = loaded.problem, title = loaded.title)
            else -> RsvpUiState(
                isLoading = false,
                title = loaded.title,
                word = player.displayWord,
                isPlaying = player.isPlaying,
                wordsPerMinute = player.settings.wordsPerMinute,
                progress = player.progress,
                page = loaded.notes.pageOf(player.index),
                pageCount = loaded.notes.pageCount,
                context = if (player.isPlaying) null else player.text.contextAround(player.index),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RsvpUiState())

    init {
        viewModelScope.launch {
            val wpm = preferences.settings.first().wordsPerMinute
            player.updateSettings(PlaybackSettings(wordsPerMinute = wpm))
            val note = repository.note(noteId)
            if (note == null) {
                loaded.value = Loaded("", null, RsvpProblem.FAILED)
                return@launch
            }
            val notes = runCatching { withContext(Dispatchers.IO) { loader.load(note.internalPath) } }.getOrNull()
            loaded.value = when {
                notes == null -> Loaded(note.displayTitle, null, RsvpProblem.FAILED)
                notes.text.isEmpty() -> Loaded(note.displayTitle, null, RsvpProblem.NO_TEXT)
                else -> {
                    player.load(notes.text, notes.startOf(startPage))
                    Loaded(note.displayTitle, notes, null)
                }
            }
        }
    }

    fun togglePlayPause() = player.togglePlayPause()

    fun pause() = player.pause()

    fun previousSentence() {
        player.skipBackward()
    }

    fun nextSentence() {
        player.skipForward()
    }

    fun seekTo(fraction: Float) {
        val size = player.state.value.text.size
        if (size > 0) player.seekTo(((size - 1) * fraction.coerceIn(0f, 1f)).toInt())
    }

    fun changeSpeed(delta: Int) {
        val wpm = WordsPerMinute.clamp(player.state.value.settings.wordsPerMinute + delta)
        player.updateSettings(player.state.value.settings.copy(wordsPerMinute = wpm))
        writeScope.launch { preferences.setWordsPerMinute(wpm) }
    }

    override fun onCleared() {
        val notes = loaded.value?.notes ?: return
        val page = notes.pageOf(player.state.value.index)
        writeScope.launch { repository.updateLastPage(noteId, page) }
    }

    companion object {
        fun factory(noteId: Long, startPage: Int): ViewModelProvider.Factory = viewModelFactory {
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
