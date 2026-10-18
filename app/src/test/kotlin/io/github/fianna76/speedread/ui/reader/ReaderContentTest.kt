package io.github.fianna76.speedread.ui.reader

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.speedread.R
import io.github.fianna76.speedread.data.Bookmark
import io.github.fianna76.speedread.data.Chapter
import io.github.fianna76.speedread.data.news.ArticleRepository
import io.github.fianna76.speedread.data.ReadingPosition
import io.github.fianna76.speedread.rsvp.PlayerState
import io.github.fianna76.speedread.rsvp.Tokenizer
import io.github.fianna76.speedread.ui.theme.SpeedReadTheme
import io.github.fianna76.speedread.testing.PHONE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class ReaderContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val text = Tokenizer.tokenize(listOf("Hello brave new world."))
    private val ui = ReaderUiState(
        isLoading = false,
        title = "A Book",
        chapters = listOf(Chapter(0, "Opening", "c0.xhtml", text.size, 0), Chapter(1, "Closing", "c1.xhtml", 10, text.size)),
        totalWords = text.size + 10,
    )

    private fun setContent(ui: ReaderUiState, player: PlayerState, actions: ReaderActions = ReaderActions()) =
        compose.setContent {
            SpeedReadTheme(dynamicColor = false) { ReaderContent(ui = ui, player = player, actions = actions) }
        }

    private fun hasClickLabel(label: String) = SemanticsMatcher("onClick label = $label") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    @Test
    fun showsTheCurrentWordAndChapter() {
        setContent(ui, PlayerState(text = text, index = 1))

        compose.onNodeWithContentDescription("brave").assertExists()
        compose.onNodeWithText("Opening").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.reader_chapter_position, 1, 2)).assertIsDisplayed()
    }

    @Test
    fun articlesShowTheirOutletAndLinkToTheOriginal() {
        var opened: String? = null
        val article = ui.copy(
            title = "Big story",
            sourceUrl = "https://news.example/story",
            sourceName = "Example News",
            chapters = listOf(Chapter(0, null, "article", text.size, 0)),
            totalWords = text.size,
        )
        setContent(article, PlayerState(text = text, index = 1), ReaderActions(onOpenOnline = { opened = it }))

        compose.onNodeWithText("Example News").assertIsDisplayed()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.reader_article_words, 4, 4)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.reader_chapters)).assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.news_open_online)).performClick()
        assertEquals("https://news.example/story", opened)
    }

    @Test
    fun singleTapZonesControlPlayback() {
        var playPause = 0
        var back = 0
        var forward = 0
        setContent(
            ui,
            PlayerState(text = text, index = 1),
            ReaderActions(
                onPlayPause = { playPause++ },
                onPreviousSentence = { back++ },
                onNextSentence = { forward++ },
            ),
        )

        compose.onNode(hasClickLabel(context.getString(R.string.reader_play))).performClick()
        compose.onNode(hasClickLabel(context.getString(R.string.reader_previous_sentence))).performClick()
        compose.onNode(hasClickLabel(context.getString(R.string.reader_next_sentence))).performClick()
        assertEquals(listOf(1, 1, 1), listOf(playPause, back, forward))
    }

    @Test
    fun transportButtonsAndSpeedButtonsWork() {
        var playPause = 0
        var speedDelta = 0
        setContent(
            ui,
            PlayerState(text = text, index = 1, isPlaying = true),
            ReaderActions(onPlayPause = { playPause++ }, onChangeWordsPerMinuteBy = { speedDelta += it }),
        )

        compose.onNodeWithContentDescription(context.getString(R.string.reader_pause)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.reader_faster)).performClick()
        compose.onNodeWithText(context.getString(R.string.reader_wpm, 300)).assertIsDisplayed()
        assertEquals(1, playPause)
        assertEquals(25, speedDelta)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun keyboardsAndPageTurnersControlPlayback() {
        var playPause = 0
        var next = 0
        var speedDelta = 0
        setContent(
            ui,
            PlayerState(text = text, index = 1),
            ReaderActions(onPlayPause = { playPause++ }, onNextSentence = { next++ }, onChangeWordsPerMinuteBy = { speedDelta += it }),
        )

        compose.onRoot().performKeyInput { pressKey(Key.Spacebar) }
        compose.onRoot().performKeyInput { pressKey(Key.PageDown) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onRoot().performKeyInput { pressKey(Key.VolumeUp) } // Volume keys are off by default.

        assertEquals(1, playPause)
        assertEquals(1, next)
        assertEquals(-25, speedDelta)
    }

    @Test
    fun bookmarksAreAddedFromTheTopBarAndListedWithTheChapters() {
        var toggles = 0
        var opened: Bookmark? = null
        val bookmark = Bookmark(7, ReadingPosition(1, 0), "The last chapter begins", createdAt = 0)
        setContent(
            ui.copy(bookmarks = listOf(bookmark)),
            PlayerState(text = text, index = 1),
            ReaderActions(onToggleBookmark = { toggles++ }, onGoToBookmark = { opened = it }),
        )

        compose.onNodeWithContentDescription(context.getString(R.string.reader_add_bookmark)).performClick()
        assertEquals(1, toggles)

        compose.onNodeWithContentDescription(context.getString(R.string.reader_chapters)).performClick()
        compose.onNodeWithText(context.getString(R.string.reader_bookmarks, 1)).performClick()
        compose.onNodeWithText("The last chapter begins\u2026").performClick()
        assertEquals(bookmark, opened)
    }

    @Test
    fun pastedTextHasNoPageToOpen() {
        setContent(
            ui.copy(sourceUrl = ArticleRepository.TEXT_URL_PREFIX + "abc", chapters = ui.chapters.take(1)),
            PlayerState(text = text, index = 1),
        )
        compose.onNodeWithText(context.getString(R.string.reader_pasted_text)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.news_open_online)).assertDoesNotExist()
    }

    @Test
    fun pageViewShowsTheTextAndATappedWordBecomesThePosition() {
        var seekedTo: Int? = null
        setContent(ui, PlayerState(text = text, index = 1), ReaderActions(onSeekToWord = { seekedTo = it }))

        compose.onNodeWithContentDescription(context.getString(R.string.reader_page_view)).performClick()
        compose.onNodeWithText("Hello brave new world.").assertIsDisplayed().performClick()

        assertTrue(seekedTo in 0 until text.size)
        // Back in RSVP view.
        compose.onNodeWithContentDescription(context.getString(R.string.reader_page_view)).assertIsDisplayed()
    }

    @Test
    fun theCurrentWordShowsItsBookmark() {
        val bookmark = Bookmark(7, ReadingPosition(0, 1), "brave new world.", createdAt = 0)
        setContent(ui.copy(bookmarks = listOf(bookmark)), PlayerState(text = text, index = 1))
        compose.onNodeWithContentDescription(context.getString(R.string.reader_remove_bookmark)).assertIsDisplayed()
    }

    @Test
    fun pausingShowsTheSurroundingText() {
        setContent(ui, PlayerState(text = text, index = 2))
        compose.onNodeWithText("Hello brave new world.").assertIsDisplayed()
    }

    @Test
    fun firstOpenExplainsTheTapZones() {
        setContent(ui.copy(showTapHint = true), PlayerState(text = text))
        compose.onNodeWithText(context.getString(R.string.reader_tap_hint)).assertIsDisplayed()
    }

    @Test
    fun finishedBooksOfferARestart() {
        var restarts = 0
        setContent(ui.copy(isFinished = true), PlayerState(text = text, index = 3), ReaderActions(onRestart = { restarts++ }))

        compose.onNodeWithText(context.getString(R.string.reader_finished_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.reader_read_again)).performClick()
        assertEquals(1, restarts)
    }
}
