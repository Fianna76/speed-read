package io.github.fianna76.speedread.ui.library

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.speedread.data.importing.ImportCoordinator
import io.github.fianna76.speedread.data.importing.ImportError
import io.github.fianna76.speedread.data.importing.ImportResult
import io.github.fianna76.speedread.data.settings.LibrarySort
import io.github.fianna76.speedread.testing.FakeBookRepository
import io.github.fianna76.speedread.testing.MainDispatcherRule
import io.github.fianna76.speedread.testing.testBook
import io.github.fianna76.speedread.testing.testPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class LibraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var repository: FakeBookRepository

    private fun TestScope.createViewModel(
        importer: suspend (Uri) -> ImportResult = { ImportResult.Failed(null, ImportError.UNREADABLE) },
    ): LibraryViewModel {
        repository = FakeBookRepository(
            books = listOf(
                testBook(1, title = "banana", author = "Zed", addedAt = 3, lastOpenedAt = null),
                testBook(2, title = "Apple", author = null, addedAt = 1, lastOpenedAt = 50),
                testBook(3, title = "Éclair", author = "Amy", addedAt = 2, lastOpenedAt = 100),
            ),
        )
        val viewModel = LibraryViewModel(
            books = repository,
            preferences = testPreferences(tmp.newFolder(), backgroundScope),
            imports = ImportCoordinator(importer, backgroundScope),
        )
        // uiState is only active while collected, like it is by the UI.
        backgroundScope.launch { viewModel.uiState.collect {} }
        return viewModel
    }

    private suspend fun LibraryViewModel.awaitState(predicate: (LibraryUiState) -> Boolean) = uiState.first(predicate)

    @Test
    fun `search matches titles and authors, ignoring case and accents`() = runTest {
        val viewModel = createViewModel()
        viewModel.awaitState { !it.isLoading }

        viewModel.setQuery("ECLAIR")
        assertEquals(listOf(3L), viewModel.awaitState { it.query == "ECLAIR" }.books.map { it.id })
        viewModel.setQuery("zed")
        val state = viewModel.awaitState { it.query == "zed" }
        assertEquals(listOf(1L), state.books.map { it.id })
        assertEquals(3, state.totalBooks)
        assertTrue(state.isFiltered)
    }

    @Test
    fun `reading filters split unread, in progress and finished books`() = runTest {
        val viewModel = createViewModel()
        repository.books.value = listOf(
            testBook(1, totalWords = 100, wordsRead = 0),
            testBook(2, totalWords = 100, wordsRead = 40),
            testBook(3, totalWords = 100, wordsRead = 100),
        )
        viewModel.awaitState { it.books.size == 3 }

        viewModel.setFilter(LibraryFilter.READING)
        assertEquals(listOf(2L), viewModel.awaitState { it.filter == LibraryFilter.READING }.books.map { it.id })
        viewModel.setFilter(LibraryFilter.NOT_STARTED)
        assertEquals(listOf(1L), viewModel.awaitState { it.filter == LibraryFilter.NOT_STARTED }.books.map { it.id })
        viewModel.setFilter(LibraryFilter.FINISHED)
        assertEquals(listOf(3L), viewModel.awaitState { it.filter == LibraryFilter.FINISHED }.books.map { it.id })
    }

    @Test
    fun `selected books go into collections, which can be shown, trimmed and deleted`() = runTest {
        val viewModel = createViewModel()
        viewModel.awaitState { !it.isLoading }
        viewModel.toggleSelection(1)
        viewModel.toggleSelection(3)

        viewModel.createCollection("  Book club ")
        val shown = viewModel.awaitState { it.selectedCollection != null }
        assertEquals("Book club", shown.selectedCollection!!.name)
        assertEquals(setOf(1L, 3L), shown.books.map { it.id }.toSet())
        assertTrue(shown.selection.isEmpty())
        assertTrue(shown.messages.any { it is LibraryMessage.Collected && it.count == 2 })

        viewModel.toggleSelection(3)
        viewModel.removeSelectedFromCollection()
        assertEquals(listOf(1L), viewModel.awaitState { it.books.size == 1 }.books.map { it.id })

        viewModel.selectCollection(null)
        viewModel.toggleSelection(2)
        viewModel.addSelectedTo(viewModel.uiState.value.collections.single())
        assertEquals(setOf(1L, 2L), viewModel.awaitState { s -> s.collections.single().bookIds.size == 2 }.collections.single().bookIds)

        viewModel.selectCollection(viewModel.uiState.value.collections.single().id)
        viewModel.deleteCollection(viewModel.uiState.value.collections.single())
        val after = viewModel.awaitState { it.collections.isEmpty() }
        assertEquals(null, after.selectedCollection)
        assertEquals(3, after.books.size) // The books stay.
    }

    @Test
    fun `recent sort shows recently read books first, then the newest additions`() = runTest {
        val viewModel = createViewModel()
        val state = viewModel.awaitState { !it.isLoading }
        assertEquals(LibrarySort.RECENT, state.sort)
        assertEquals(listOf(3L, 2L, 1L), state.books.map { it.id })
    }

    @Test
    fun `title sort is case- and accent-insensitive`() = runTest {
        val viewModel = createViewModel()
        viewModel.setSort(LibrarySort.TITLE)
        val state = viewModel.awaitState { it.sort == LibrarySort.TITLE }
        assertEquals(listOf("Apple", "banana", "Éclair"), state.books.map { it.title })
    }

    @Test
    fun `author sort puts books without an author last`() = runTest {
        val viewModel = createViewModel()
        viewModel.setSort(LibrarySort.AUTHOR)
        val state = viewModel.awaitState { it.sort == LibrarySort.AUTHOR }
        assertEquals(listOf("Amy", "Zed", null), state.books.map { it.author })
    }

    @Test
    fun `selection can be toggled, extended to all books and cleared`() = runTest {
        val viewModel = createViewModel()
        viewModel.awaitState { !it.isLoading }

        viewModel.toggleSelection(2)
        assertEquals(setOf(2L), viewModel.awaitState { it.isSelecting }.selection)
        viewModel.selectAll()
        assertEquals(setOf(1L, 2L, 3L), viewModel.awaitState { it.selection.size == 3 }.selection)
        viewModel.toggleSelection(1)
        assertEquals(setOf(2L, 3L), viewModel.awaitState { it.selection.size == 2 }.selection)
        viewModel.clearSelection()
        assertFalse(viewModel.awaitState { !it.isSelecting }.isSelecting)
    }

    @Test
    fun `deleting the selection removes the books and reports it`() = runTest {
        val viewModel = createViewModel()
        viewModel.awaitState { !it.isLoading }
        viewModel.toggleSelection(1)
        viewModel.toggleSelection(3)
        viewModel.awaitState { it.selection.size == 2 }

        viewModel.deleteSelected()
        val state = viewModel.awaitState { it.messages.isNotEmpty() }
        assertEquals(listOf(2L), state.books.map { it.id })
        assertTrue(state.selection.isEmpty())
        val message = state.messages.single() as LibraryMessage.Deleted
        assertEquals(2, message.count)

        viewModel.onMessageShown(message.id)
        assertTrue(viewModel.awaitState { it.messages.isEmpty() }.messages.isEmpty())
    }

    @Test
    fun `import results are queued as messages`() = runTest {
        val viewModel = createViewModel(importer = { uri ->
            if (uri.lastPathSegment == "good.epub") ImportResult.Imported(42, "Good Book")
            else ImportResult.Failed("bad.epub", ImportError.INVALID_FILE)
        })
        viewModel.importBooks(listOf(Uri.parse("content://test/good.epub"), Uri.parse("content://test/bad.epub")))

        val state = viewModel.awaitState { it.messages.size == 2 && it.importsInProgress == 0 }
        val results = state.messages.map { (it as LibraryMessage.Import).result }
        assertEquals(ImportResult.Imported(42, "Good Book"), results[0])
        assertEquals(ImportResult.Failed("bad.epub", ImportError.INVALID_FILE), results[1])
    }
}
