package io.github.fianna76.speedread.rsvp

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RsvpPlayerTest {

    // 600 WPM without pacing adjustments: exactly 100 ms per word.
    private val flat = PlaybackSettings(wordsPerMinute = 600, smartPacing = false, rampUp = false)

    // One0 two.1 | Three2 four3 five.4 | Six5 seven.6
    private val sentences = Tokenizer.tokenize(listOf("One two. Three four five. Six seven."))

    private fun TestScope.player(text: TokenizedText, onEnd: () -> Unit = {}): RsvpPlayer =
        RsvpPlayer(backgroundScope, clock = { testScheduler.currentTime }, onEndReached = onEnd).apply {
            updateSettings(flat)
            load(text)
        }

    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun `advances one word per interval while playing`() = runTest {
        val player = player(sentences)
        player.play()
        runCurrent()
        assertTrue(player.state.value.isPlaying)
        assertEquals(0, player.state.value.index)

        advance(100)
        assertEquals(1, player.state.value.index)
        advance(300)
        assertEquals(4, player.state.value.index)
    }

    @Test
    fun `chunk mode shows phrases for as long as their words would take`() = runTest {
        val player = player(sentences)
        player.updateSettings(flat.copy(chunkSize = 2))
        assertEquals("One two.", player.state.value.displayWord!!.text)
        player.play()
        runCurrent()

        advance(200) // Two words' worth.
        assertEquals(2, player.state.value.index)
        assertEquals("Three four", player.state.value.displayWord!!.text)
        advance(200)
        assertEquals("five.", player.state.value.displayWord!!.text)
        advance(100)
        assertEquals("Six seven.", player.state.value.displayWord!!.text)
        advance(200)
        assertFalse(player.state.value.isPlaying)
    }

    @Test
    fun `pause freezes the current word`() = runTest {
        val player = player(sentences)
        player.play()
        advance(150)
        player.pause()
        advance(1_000)
        assertEquals(1, player.state.value.index)
        assertFalse(player.state.value.isPlaying)

        player.togglePlayPause()
        advance(100)
        assertEquals(2, player.state.value.index)
    }

    @Test
    fun `reaching the end stops playback and notifies`() = runTest {
        var ended = 0
        val player = player(Tokenizer.tokenize(listOf("one two")), onEnd = { ended++ })
        player.play()
        advance(150)
        assertEquals(1, player.state.value.index)
        assertEquals(0, ended)

        advance(100)
        assertEquals(1, ended)
        assertFalse(player.state.value.isPlaying)
        assertTrue(player.state.value.isAtEnd)
    }

    @Test
    fun `speed changes apply from the next word`() = runTest {
        val player = player(sentences)
        player.play()
        advance(100)
        assertEquals(1, player.state.value.index)

        player.updateSettings(flat.copy(wordsPerMinute = 300)) // 200 ms per word
        advance(100) // word 1 was scheduled at the old speed
        assertEquals(2, player.state.value.index)
        advance(100)
        assertEquals(2, player.state.value.index)
        advance(100)
        assertEquals(3, player.state.value.index)
    }

    @Test
    fun `ramp-up shows the first words longer`() = runTest {
        val player = player(sentences)
        player.updateSettings(flat.copy(rampUp = true))
        player.play()
        advance(199)
        assertEquals(0, player.state.value.index)
        advance(1)
        assertEquals(1, player.state.value.index) // 200 ms (x2.0)
        advance(160)
        assertEquals(2, player.state.value.index) // 160 ms (x1.6)
    }

    @Test
    fun `skip forward jumps to the next sentence`() = runTest {
        val player = player(sentences)
        player.seekTo(3)
        assertTrue(player.skipForward())
        assertEquals(5, player.state.value.index)
        assertFalse(player.skipForward())
        assertEquals(5, player.state.value.index)
    }

    @Test
    fun `skip backward restarts the sentence, then goes to the previous one`() = runTest {
        val player = player(sentences)
        player.seekTo(3)
        assertTrue(player.skipBackward())
        assertEquals(2, player.state.value.index)
        assertTrue(player.skipBackward())
        assertEquals(0, player.state.value.index)
        assertFalse(player.skipBackward())
    }

    @Test
    fun `skip backward while playing allows for reaction time`() = runTest {
        val player = player(sentences)
        player.seekTo(2)
        player.play()
        advance(100) // now on word 3, one word into the second sentence
        assertTrue(player.skipBackward())
        assertEquals(0, player.state.value.index)
        assertTrue(player.state.value.isPlaying)
    }

    @Test
    fun `seeking while playing keeps playing from the new position`() = runTest {
        val player = player(sentences)
        player.play()
        advance(50)
        player.seekTo(5)
        assertTrue(player.state.value.isPlaying)
        advance(100)
        assertEquals(6, player.state.value.index)
    }

    @Test
    fun `loading new text stops playback and clamps the start index`() = runTest {
        val player = player(sentences)
        player.play()
        advance(100)
        player.load(Tokenizer.tokenize(listOf("a b c")), startIndex = 10)
        assertFalse(player.state.value.isPlaying)
        assertEquals(2, player.state.value.index)
        advance(1_000)
        assertEquals(2, player.state.value.index)
    }

    @Test
    fun `playing empty text is a no-op`() = runTest {
        val player = player(TokenizedText.EMPTY)
        player.play()
        runCurrent()
        assertFalse(player.state.value.isPlaying)
        assertFalse(player.skipForward())
        assertFalse(player.skipBackward())
    }
}
