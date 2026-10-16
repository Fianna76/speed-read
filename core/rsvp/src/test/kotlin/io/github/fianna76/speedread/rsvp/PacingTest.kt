package io.github.fianna76.speedread.rsvp

import org.junit.Assert.assertEquals
import org.junit.Test

class PacingTest {

    private fun word(text: String, pause: Pause = Pause.NONE) = Tokenizer.tokenize(listOf(text)).words.first()
        .copy(pause = pause)

    @Test
    fun `base interval follows words per minute`() {
        assertEquals(200.0, Pacing.baseIntervalMillis(300), 0.0)
        assertEquals(60.0, Pacing.baseIntervalMillis(1000), 0.0)
    }

    @Test
    fun `smart pacing lengthens punctuation, long words and numbers`() {
        assertEquals(1.0, Pacing.multiplier(word("cat"), smartPacing = true), 1e-9)
        assertEquals(1.5, Pacing.multiplier(word("cat,", Pause.CLAUSE), smartPacing = true), 1e-9)
        assertEquals(2.1, Pacing.multiplier(word("cat.", Pause.SENTENCE), smartPacing = true), 1e-9)
        assertEquals(2.6, Pacing.multiplier(word("cat.", Pause.PARAGRAPH), smartPacing = true), 1e-9)
        // 13 letters: 6 over the threshold of 7 -> +0.48
        assertEquals(1.48, Pacing.multiplier(word("extraordinary"), smartPacing = true), 1e-9)
        // Very long words are capped at +0.6
        assertEquals(1.6, Pacing.multiplier(word("incomprehensibilities"), smartPacing = true), 1e-9)
        assertEquals(1.3, Pacing.multiplier(word("1984"), smartPacing = true), 1e-9)
    }

    @Test
    fun `without smart pacing every word gets the base interval`() {
        assertEquals(1.0, Pacing.multiplier(word("extraordinary.", Pause.PARAGRAPH), smartPacing = false), 0.0)
    }

    @Test
    fun `ramp-up slows only the first few words`() {
        val settings = PlaybackSettings(wordsPerMinute = 600, smartPacing = false, rampUp = true)
        val durations = (0..5).map { Pacing.durationMillis(word("cat"), settings, wordsShown = it) }
        assertEquals(listOf(200L, 160L, 130L, 110L, 100L, 100L), durations)
    }

    @Test
    fun `estimates reading time`() {
        val plain = PlaybackSettings(wordsPerMinute = 300, smartPacing = false)
        assertEquals(60_000L, Pacing.estimateMillis(300, plain))
        val smart = plain.copy(smartPacing = true)
        assertEquals(72_000L, Pacing.estimateMillis(300, smart))
    }

    @Test
    fun `words per minute are clamped to the supported range`() {
        assertEquals(WordsPerMinute.MIN, WordsPerMinute.clamp(10))
        assertEquals(WordsPerMinute.MAX, WordsPerMinute.clamp(5000))
        assertEquals(450, WordsPerMinute.clamp(450))
    }
}
