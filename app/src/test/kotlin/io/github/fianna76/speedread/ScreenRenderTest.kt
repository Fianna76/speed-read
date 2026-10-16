package io.github.fianna76.speedread

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.speedread.data.Chapter
import io.github.fianna76.speedread.data.settings.UserPreferences
import io.github.fianna76.speedread.rsvp.PlayerState
import io.github.fianna76.speedread.rsvp.Tokenizer
import io.github.fianna76.speedread.testing.testBook
import io.github.fianna76.speedread.ui.library.LibraryContent
import io.github.fianna76.speedread.ui.library.LibraryUiState
import io.github.fianna76.speedread.ui.reader.ReaderActions
import io.github.fianna76.speedread.ui.reader.ReaderContent
import io.github.fianna76.speedread.ui.reader.ReaderUiState
import io.github.fianna76.speedread.ui.settings.SettingsContent
import io.github.fianna76.speedread.ui.theme.SpeedReadTheme
import io.github.fianna76.speedread.testing.PHONE
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders every screen in light and dark themes on a phone-sized display with real graphics.
 * Acts as a smoke test (each screen composes, lays out and draws without crashing) and writes
 * PNGs to `app/build/outputs/screenshots/` for visual review.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE)
class ScreenRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private val books = listOf(
        testBook(1, title = "Pride and Prejudice", author = "Jane Austen", totalWords = 120_000, wordsRead = 52_000),
        testBook(2, title = "Moby-Dick; or, The Whale", author = "Herman Melville"),
        testBook(3, title = "The Time Machine", author = "H. G. Wells", totalWords = 32_000, wordsRead = 32_000),
        testBook(4, title = "Meditations", author = "Marcus Aurelius", totalWords = 60_000, wordsRead = 9_000),
    )

    private val paragraph = Tokenizer.tokenize(
        listOf(
            "It is a truth universally acknowledged, that a single man in possession of a good fortune, " +
                "must be in want of a wife.",
        ),
    )
    private val readerUi = ReaderUiState(
        isLoading = false,
        title = "Pride and Prejudice",
        chapters = List(61) { Chapter(it, "Chapter ${it + 1}", "c$it.xhtml", 2_000, it * 2_000) },
        chapterIndex = 26,
        totalWords = 122_000,
    )

    @Test
    fun library() = capture("library") {
        LibraryContent(
            state = LibraryUiState(isLoading = false, books = books),
            onImport = {}, onOpenBook = {}, onOpenSettings = {}, onSortChange = {}, onToggleSelection = {},
            onSelectAll = {}, onClearSelection = {}, onDeleteSelected = {}, onMessageShown = {},
        )
    }

    @Test
    fun librarySelection() = capture("library_selection") {
        LibraryContent(
            state = LibraryUiState(isLoading = false, books = books, selection = setOf(2L, 4L)),
            onImport = {}, onOpenBook = {}, onOpenSettings = {}, onSortChange = {}, onToggleSelection = {},
            onSelectAll = {}, onClearSelection = {}, onDeleteSelected = {}, onMessageShown = {},
        )
    }

    @Test
    fun libraryEmpty() = capture("library_empty") {
        LibraryContent(
            state = LibraryUiState(isLoading = false),
            onImport = {}, onOpenBook = {}, onOpenSettings = {}, onSortChange = {}, onToggleSelection = {},
            onSelectAll = {}, onClearSelection = {}, onDeleteSelected = {}, onMessageShown = {},
        )
    }

    @Test
    fun readerPlaying() = capture("reader_playing") {
        ReaderContent(readerUi, PlayerState(text = paragraph, index = 3, isPlaying = true), ReaderActions())
    }

    @Test
    fun readerPaused() = capture("reader_paused") {
        ReaderContent(readerUi, PlayerState(text = paragraph, index = 13), ReaderActions())
    }

    @Test
    fun readerFinished() = capture("reader_finished") {
        ReaderContent(readerUi.copy(isFinished = true), PlayerState(text = paragraph, index = 22), ReaderActions())
    }

    @Test
    fun settings() = capture("settings") {
        SettingsContent(
            preferences = UserPreferences(),
            onBack = {}, onWordsPerMinuteChange = {}, onSmartPacingChange = {}, onRampUpChange = {},
            onFontSizeChange = {}, onFontChange = {}, onHighlightChange = {}, onShowContextChange = {},
            onThemeModeChange = {},
        )
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        var dark by mutableStateOf(false)
        compose.setContent { SpeedReadTheme(darkTheme = dark, dynamicColor = false, content = content) }
        save(name)
        dark = true
        compose.waitForIdle()
        save("${name}_dark")
    }

    private fun save(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = File("build/outputs/screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
