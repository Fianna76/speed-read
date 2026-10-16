package io.github.fianna76.speedread.ui.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.speedread.data.Chapter
import io.github.fianna76.speedread.rsvp.PlaybackSettings
import io.github.fianna76.speedread.rsvp.PlayerState
import io.github.fianna76.speedread.rsvp.Tokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class) // android.icu is part of the Android framework.
class ReaderProgressTest {

    private val text = Tokenizer.tokenize(listOf("a b c d e"))
    private val ui = ReaderUiState(
        isLoading = false,
        chapters = listOf(Chapter(0, null, "a", 5, 0), Chapter(1, null, "b", 5, 5)),
        chapterIndex = 1,
        totalWords = 10,
    )

    @Test
    fun `combines chapter offset and player position`() {
        val settings = PlaybackSettings(wordsPerMinute = 300, smartPacing = false)
        val progress = ReaderProgress.of(ui, PlayerState(text = text, index = 2, settings = settings))

        assertEquals(0.5f, progress.chapterFraction)
        assertEquals(0.7f, progress.bookFraction)
        assertEquals(600L, progress.bookMillisLeft) // 3 words at 200 ms
    }

    @Test
    fun `a finished book is complete`() {
        val progress = ReaderProgress.of(ui.copy(isFinished = true), PlayerState(text = text, index = 2))
        assertEquals(1f, progress.chapterFraction)
        assertEquals(1f, progress.bookFraction)
        assertEquals(0L, progress.bookMillisLeft)
    }

    @Test
    fun `durations round up to whole minutes`() {
        assertNull(formatDuration(0, Locale.US))
        assertEquals("1 min", formatDuration(1_000, Locale.US))
        assertEquals("45 min", formatDuration(45 * 60_000L, Locale.US))
        assertEquals("2 hr", formatDuration(120 * 60_000L, Locale.US))
        assertEquals("1 hr, 5 min", formatDuration(64 * 60_000L + 1, Locale.US))
    }
}
