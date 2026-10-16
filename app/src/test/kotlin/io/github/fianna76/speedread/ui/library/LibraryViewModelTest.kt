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
            else ImportResult.Failed("bad.epub", ImportError.NOT_AN_EPUB)
        })
        viewModel.importBooks(listOf(Uri.parse("content://test/good.epub"), Uri.parse("content://test/bad.epub")))

        val state = viewModel.awaitState { it.messages.size == 2 && it.importsInProgress == 0 }
        val results = state.messages.map { (it as LibraryMessage.Import).result }
        assertEquals(ImportResult.Imported(42, "Good Book"), results[0])
        assertEquals(ImportResult.Failed("bad.epub", ImportError.NOT_AN_EPUB), results[1])
    }
}
