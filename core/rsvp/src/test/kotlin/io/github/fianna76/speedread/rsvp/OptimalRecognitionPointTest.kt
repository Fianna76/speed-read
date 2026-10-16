package io.github.fianna76.speedread.rsvp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OptimalRecognitionPointTest {

    private fun orp(word: String): String = word.substring(OptimalRecognitionPoint.rangeIn(word))

    @Test
    fun `fixation point moves right as words get longer`() {
        assertEquals("a", orp("a"))
        assertEquals("o", orp("word"))
        assertEquals("a", orp("reading"))
        assertEquals("p", orp("comprehension"))
        assertEquals("r", orp("internationalization"))
    }

    @Test
    fun `surrounding punctuation does not shift the fixation point`() {
        assertEquals(1, OptimalRecognitionPoint.rangeIn("Hello,").first)
        assertEquals(2, OptimalRecognitionPoint.rangeIn("\u201CHello,").first)
        assertEquals("e", orp("(Hello!)"))
    }

    @Test
    fun `apostrophes and hyphens are not counted as letters`() {
        assertEquals("o", orp("don't"))
        assertEquals("l", orp("well-known"))
    }

    @Test
    fun `punctuation-only tokens fixate on the middle character`() {
        assertEquals("\u2014", orp("\u2014"))
        assertEquals(1, OptimalRecognitionPoint.rangeIn("...").first)
    }

    @Test
    fun `surrogate pairs and combining marks stay intact`() {
        // "été" with decomposed accents: the ORP is the "t", never a lone combining mark.
        assertEquals("t", orp("e\u0301te\u0301"))
        // A decomposed "é" at the ORP keeps its accent.
        assertEquals("e\u0301", orp("ce\u0301"))
        // Supplementary-plane letters (here: mathematical bold) are kept whole.
        val bold = "𝐀𝐁𝐂"
        val range = OptimalRecognitionPoint.rangeIn(bold)
        assertEquals(2, range.first)
        assertEquals(2, range.count())
    }

    @Test
    fun `empty input yields an empty range`() {
        assertTrue(OptimalRecognitionPoint.rangeIn("").isEmpty())
    }
}
