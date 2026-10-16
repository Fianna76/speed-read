package io.github.fianna76.speedread.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.fianna76.speedread.SpeedReadApplication
import io.github.fianna76.speedread.data.Book
import io.github.fianna76.speedread.data.BookRepository
import io.github.fianna76.speedread.data.importing.ImportCoordinator
import io.github.fianna76.speedread.data.importing.ImportResult
import io.github.fianna76.speedread.data.settings.LibrarySort
import io.github.fianna76.speedread.data.settings.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Collator

data class LibraryUiState(
    val isLoading: Boolean = true,
    val books: List<Book> = emptyList(),
    val sort: LibrarySort = LibrarySort.RECENT,
    val selection: Set<Long> = emptySet(),
    val importsInProgress: Int = 0,
    /** Pending one-off messages, oldest first; the UI shows the head and acknowledges it. */
    val messages: List<LibraryMessage> = emptyList(),
) {
    val isSelecting: Boolean get() = selection.isNotEmpty()
}

/** Snackbar-style feedback. Modelled as state (not events) so nothing is lost on rotation. */
sealed interface LibraryMessage {
    val id: Long

    data class Import(override val id: Long, val result: ImportResult) : LibraryMessage

    data class Deleted(override val id: Long, val count: Int) : LibraryMessage
}

class LibraryViewModel(
    private val books: BookRepository,
    private val preferences: UserPreferencesRepository,
    private val imports: ImportCoordinator,
) : ViewModel() {

    private val selection = MutableStateFlow<Set<Long>>(emptySet())
    private val messages = MutableStateFlow<List<LibraryMessage>>(emptyList())
    private var nextMessageId = 0L

    val uiState: StateFlow<LibraryUiState> = combine(
        books.observeBooks(),
        preferences.preferences.map { it.librarySort }.distinctUntilChanged(),
        selection,
        imports.pendingCount,
        messages,
    ) { library, sort, selected, pending, pendingMessages ->
        val ids = library.mapTo(HashSet()) { it.id }
        LibraryUiState(
            isLoading = false,
            books = library.sortedWith(sort.comparator()),
            sort = sort,
            // Drop ids of books that disappeared (e.g. deleted elsewhere).
            selection = selected.filterTo(LinkedHashSet()) { it in ids },
            importsInProgress = pending,
            messages = pendingMessages,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState())

    init {
        viewModelScope.launch {
            imports.results.collect { result -> postMessage { id -> LibraryMessage.Import(id, result) } }
        }
    }

    fun importBooks(uris: List<Uri>) = imports.enqueue(uris)

    fun setSort(sort: LibrarySort) {
        viewModelScope.launch { preferences.setLibrarySort(sort) }
    }

    fun toggleSelection(bookId: Long) = selection.update { if (bookId in it) it - bookId else it + bookId }

    fun selectAll() = selection.update { uiState.value.books.mapTo(LinkedHashSet()) { it.id } }

    fun clearSelection() = selection.update { emptySet() }

    fun deleteSelected() {
        val ids = uiState.value.selection
        if (ids.isEmpty()) return
        clearSelection()
        viewModelScope.launch {
            books.deleteBooks(ids)
            postMessage { id -> LibraryMessage.Deleted(id, ids.size) }
        }
    }

    fun onMessageShown(messageId: Long) = messages.update { queue -> queue.filterNot { it.id == messageId } }

    private fun postMessage(create: (Long) -> LibraryMessage) {
        val message = create(nextMessageId++)
        messages.update { it + message }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as SpeedReadApplication).container
                LibraryViewModel(container.bookRepository, container.preferencesRepository, container.importCoordinator)
            }
        }
    }
}

/** Library ordering; text is compared with the user's locale rules, ignoring case and accents. */
internal fun LibrarySort.comparator(): Comparator<Book> {
    val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
    val byTitle = Comparator<Book> { a, b -> collator.compare(a.title, b.title) }
    return when (this) {
        LibrarySort.RECENT -> compareByDescending<Book> { it.lastOpenedAt ?: Long.MIN_VALUE }
            .thenByDescending { it.addedAt }
        LibrarySort.TITLE -> byTitle
        LibrarySort.AUTHOR -> Comparator<Book> { a, b ->
            when {
                a.author == null && b.author == null -> 0
                a.author == null -> 1
                b.author == null -> -1
                else -> collator.compare(a.author, b.author)
            }
        }.then(byTitle)
        LibrarySort.ADDED -> compareByDescending { it.addedAt }
    }
}
