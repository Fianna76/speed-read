package io.github.fianna76.speedread.ui.library

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import io.github.fianna76.speedread.data.BookCollection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.speedread.R
import io.github.fianna76.speedread.testing.testBook
import io.github.fianna76.speedread.ui.theme.SpeedReadTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import io.github.fianna76.speedread.testing.PHONE
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class LibraryContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val books = listOf(
        testBook(1, title = "Pride and Prejudice", author = "Jane Austen", totalWords = 100, wordsRead = 42),
        testBook(2, title = "Moby-Dick", author = "Herman Melville"),
    )

    private fun setContent(
        state: LibraryUiState,
        onImport: () -> Unit = {},
        onOpenBook: (Long) -> Unit = {},
        onToggleSelection: (Long) -> Unit = {},
        onDeleteSelected: () -> Unit = {},
        organise: LibraryOrganiseActions = LibraryOrganiseActions(),
    ) = compose.setContent {
        SpeedReadTheme(dynamicColor = false) {
            LibraryContent(
                state = state,
                onImport = onImport,
                onOpenBook = onOpenBook,
                onOpenSettings = {},
                onSortChange = {},
                onToggleSelection = onToggleSelection,
                onSelectAll = {},
                onClearSelection = {},
                onDeleteSelected = onDeleteSelected,
                onMessageShown = {},
                organise = organise,
            )
        }
    }

    @Test
    fun searchFiltersAndCollectionsAreOffered() {
        val queries = mutableListOf<String>()
        val filters = mutableListOf<LibraryFilter>()
        val shownCollections = mutableListOf<Long?>()
        val clubs = BookCollection(9, "Book club", setOf(1))
        setContent(
            LibraryUiState(isLoading = false, books = books, collections = listOf(clubs)),
            organise = LibraryOrganiseActions(
                onQueryChange = { queries += it },
                onFilterChange = { filters += it },
                onSelectCollection = { shownCollections += it },
            ),
        )

        compose.onNodeWithText(context.getString(R.string.library_filter_reading)).performClick()
        compose.onNodeWithText("Book club (1)").performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.library_search)).performClick()
        compose.onNodeWithText(context.getString(R.string.library_search_hint)).performTextInput("austen")

        assertEquals(listOf(LibraryFilter.READING), filters)
        assertEquals(listOf<Long?>(9), shownCollections)
        assertEquals(listOf("austen"), queries)
    }

    @Test
    fun selectedBooksCanBeAddedToACollection() {
        val added = mutableListOf<BookCollection>()
        val clubs = BookCollection(9, "Book club", setOf(2))
        setContent(
            LibraryUiState(isLoading = false, books = books, selection = setOf(1), collections = listOf(clubs)),
            organise = LibraryOrganiseActions(onAddSelectedTo = { added += it }),
        )

        compose.onNodeWithContentDescription(context.getString(R.string.library_add_to_collection)).performClick()
        compose.onNodeWithText(context.getString(R.string.library_new_collection_ellipsis)).assertIsDisplayed()
        compose.onNodeWithText("Book club").performClick()

        assertEquals(listOf(clubs), added)
    }

    @Test
    fun noMatchesOffersToShowEverything() {
        val filters = mutableListOf<LibraryFilter>()
        setContent(
            LibraryUiState(isLoading = false, books = emptyList(), totalBooks = 2, filter = LibraryFilter.FINISHED),
            organise = LibraryOrganiseActions(onFilterChange = { filters += it }),
        )
        compose.onNodeWithText(context.getString(R.string.library_no_matches)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.library_show_all)).performClick()
        assertEquals(listOf(LibraryFilter.ALL), filters)
    }

    @Test
    fun emptyLibraryInvitesAnImport() {
        var imports = 0
        setContent(LibraryUiState(isLoading = false), onImport = { imports++ })

        compose.onNodeWithText(context.getString(R.string.library_empty_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.library_import)).performClick()
        assertEquals(1, imports)
    }

    @Test
    fun booksShowProgressAndOpenOnTap() {
        var opened: Long? = null
        setContent(LibraryUiState(isLoading = false, books = books), onOpenBook = { opened = it })

        compose.onNodeWithText("42%").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.book_new)).assertIsDisplayed()
        compose.onNodeWithText("Pride and Prejudice").performClick()
        assertEquals(1L, opened)
    }

    @Test
    fun longPressStartsSelection() {
        var toggled: Long? = null
        setContent(LibraryUiState(isLoading = false, books = books), onToggleSelection = { toggled = it })

        compose.onNodeWithText("Moby-Dick").performTouchInput { longClick() }
        assertEquals(2L, toggled)
    }

    @Test
    fun deletingASelectionAsksForConfirmation() {
        var deleted = false
        setContent(
            LibraryUiState(isLoading = false, books = books, selection = setOf(1L, 2L)),
            onDeleteSelected = { deleted = true },
        )

        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.library_selected, 2, 2)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.action_delete)).performClick()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.library_delete_title, 2, 2)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.action_delete)).performClick()
        assertTrue(deleted)
    }
}
