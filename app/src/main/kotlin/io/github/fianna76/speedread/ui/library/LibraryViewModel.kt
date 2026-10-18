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
import io.github.fianna76.speedread.data.BookCollection
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
import java.text.Normalizer
import java.util.Locale

/** Reading-state filters of the library. */
enum class LibraryFilter { ALL, READING, NOT_STARTED, FINISHED }

data class LibraryUiState(
    val isLoading: Boolean = true,
    /** Books shown: the library after search, filter and collection. */
    val books: List<Book> = emptyList(),
    /** Books in the library, before filtering (tells "empty library" from "no matches"). */
    val totalBooks: Int = books.size,
    val query: String = "",
    val filter: LibraryFilter = LibraryFilter.ALL,
    val collections: List<BookCollection> = emptyList(),
    /** The collection shown, or `null` for the whole library. */
    val selectedCollection: BookCollection? = null,
    val sort: LibrarySort = LibrarySort.RECENT,
    val selection: Set<Long> = emptySet(),
    val importsInProgress: Int = 0,
    /** Pending one-off messages, oldest first; the UI shows the head and acknowledges it. */
    val messages: List<LibraryMessage> = emptyList(),
) {
    val isSelecting: Boolean get() = selection.isNotEmpty()

    val isFiltered: Boolean get() = query.isNotBlank() || filter != LibraryFilter.ALL || selectedCollection != null
}

/** Snackbar-style feedback. Modelled as state (not events) so nothing is lost on rotation. */
sealed interface LibraryMessage {
    val id: Long

    data class Import(override val id: Long, val result: ImportResult) : LibraryMessage

    data class Deleted(override val id: Long, val count: Int) : LibraryMessage

    /** [count] books were added to (or, if [removed], taken off) the collection [name]. */
    data class Collected(override val id: Long, val count: Int, val name: String, val removed: Boolean = false) : LibraryMessage
}

class LibraryViewModel(
    private val books: BookRepository,
    private val preferences: UserPreferencesRepository,
    private val imports: ImportCoordinator,
) : ViewModel() {

    private val selection = MutableStateFlow<Set<Long>>(emptySet())
    private val view = MutableStateFlow(ViewState())

    private data class ViewState(val query: String = "", val filter: LibraryFilter = LibraryFilter.ALL, val collectionId: Long? = null)

    private val library = combine(books.observeBooks(), books.observeCollections(), view) { all, collections, chosen ->
        val collection = collections.firstOrNull { it.id == chosen.collectionId }
        val shown = all.filter { book ->
            (collection == null || book.id in collection.bookIds) && chosen.filter.matches(book) && book.matches(chosen.query)
        }
        Triple(all.size, shown, Triple(collections, collection, chosen))
    }
    private val messages = MutableStateFlow<List<LibraryMessage>>(emptyList())
    private var nextMessageId = 0L

    val uiState: StateFlow<LibraryUiState> = combine(
        library,
        preferences.preferences.map { it.librarySort }.distinctUntilChanged(),
        selection,
        imports.pendingCount,
        messages,
    ) { (total, shown, extra), sort, selected, pending, pendingMessages ->
        val (collections, collection, chosen) = extra
        val ids = shown.mapTo(HashSet()) { it.id }
        LibraryUiState(
            isLoading = false,
            books = shown.sortedWith(sort.comparator()),
            totalBooks = total,
            query = chosen.query,
            filter = chosen.filter,
            collections = collections,
            selectedCollection = collection,
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

    fun setQuery(query: String) = view.update { it.copy(query = query) }

    fun setFilter(filter: LibraryFilter) = view.update { it.copy(filter = filter) }

    /** Shows one collection (`null` for the whole library). */
    fun selectCollection(collectionId: Long?) = view.update { it.copy(collectionId = collectionId) }

    /** Makes a collection from the selected books (it may start empty) and shows it. */
    fun createCollection(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val ids = uiState.value.selection
        clearSelection()
        viewModelScope.launch {
            val id = books.createCollection(trimmed, ids)
            if (ids.isNotEmpty()) postMessage { messageId -> LibraryMessage.Collected(messageId, ids.size, trimmed) }
            selectCollection(id)
        }
    }

    fun addSelectedTo(collection: BookCollection) {
        val ids = uiState.value.selection
        if (ids.isEmpty()) return
        clearSelection()
        viewModelScope.launch {
            books.addToCollection(collection.id, ids)
            postMessage { id -> LibraryMessage.Collected(id, ids.size, collection.name) }
        }
    }

    /** Takes the selected books off the collection being shown (they stay in the library). */
    fun removeSelectedFromCollection() {
        val collection = uiState.value.selectedCollection ?: return
        val ids = uiState.value.selection
        if (ids.isEmpty()) return
        clearSelection()
        viewModelScope.launch {
            books.removeFromCollection(collection.id, ids)
            postMessage { id -> LibraryMessage.Collected(id, ids.size, collection.name, removed = true) }
        }
    }

    fun renameCollection(collection: BookCollection, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { books.renameCollection(collection.id, name) }
    }

    fun deleteCollection(collection: BookCollection) {
        if (view.value.collectionId == collection.id) selectCollection(null)
        viewModelScope.launch { books.deleteCollection(collection.id) }
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

/** Title or author contains [query], ignoring case and accents. */
internal fun Book.matches(query: String): Boolean {
    val needle = query.trim().fold()
    if (needle.isEmpty()) return true
    return title.fold().contains(needle) || author?.fold()?.contains(needle) == true
}

private fun String.fold(): String =
    Normalizer.normalize(lowercase(Locale.getDefault()), Normalizer.Form.NFD).replace(COMBINING_MARKS, "")

private val COMBINING_MARKS = Regex("\\p{Mn}+")

internal fun LibraryFilter.matches(book: Book): Boolean = when (this) {
    LibraryFilter.ALL -> true
    LibraryFilter.READING -> book.wordsRead > 0 && book.progress < 1f
    LibraryFilter.NOT_STARTED -> book.wordsRead == 0
    LibraryFilter.FINISHED -> book.totalWords > 0 && book.progress >= 1f
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
