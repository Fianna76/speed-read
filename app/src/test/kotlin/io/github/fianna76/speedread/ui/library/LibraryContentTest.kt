package io.github.fianna76.speedread.ui.library

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
            )
        }
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
