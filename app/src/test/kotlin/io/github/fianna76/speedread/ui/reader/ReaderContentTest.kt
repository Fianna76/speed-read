package io.github.fianna76.speedread.ui.reader

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.speedread.R
import io.github.fianna76.speedread.data.Chapter
import io.github.fianna76.speedread.rsvp.PlayerState
import io.github.fianna76.speedread.rsvp.Tokenizer
import io.github.fianna76.speedread.ui.theme.SpeedReadTheme
import org.junit.Assert.assertEquals
import io.github.fianna76.speedread.testing.PHONE
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
        chapters = listOf(Chapter(0, "Opening", "c0.xhtml", text.size, 0)),
        totalWords = text.size,
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
        compose.onNodeWithText(context.getString(R.string.reader_chapter_position, 1, 1)).assertIsDisplayed()
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
