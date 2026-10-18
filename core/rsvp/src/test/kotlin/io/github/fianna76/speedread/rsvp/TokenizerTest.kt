package io.github.fianna76.speedread.rsvp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenizerTest {

    private fun words(vararg paragraphs: String) = Tokenizer.tokenize(paragraphs.toList()).words

    @Test
    fun `splits on whitespace and keeps punctuation attached`() {
        val text = words("Hello,  world!\tHow are\u00A0you?")
        assertEquals(listOf("Hello,", "world!", "How", "are", "you?"), text.map { it.text })
    }

    @Test
    fun `assigns pauses for clauses, sentences and paragraph ends`() {
        val text = words("Yes, it works. Really")
        assertEquals(
            listOf(Pause.CLAUSE, Pause.NONE, Pause.SENTENCE, Pause.PARAGRAPH),
            text.map { it.pause },
        )
    }

    @Test
    fun `abbreviations and initials do not end sentences`() {
        val text = Tokenizer.tokenize(listOf("Mr. Smith met J. R. R. Tolkien in the U.S. Army. He left."))
        assertEquals(2, text.sentenceCount)
        assertEquals(Pause.NONE, text.words.first { it.text == "Mr." }.pause)
        assertEquals(Pause.NONE, text.words.first { it.text == "J." }.pause)
        assertEquals(Pause.SENTENCE, text.words.first { it.text == "Army." }.pause)
    }

    @Test
    fun `terminal punctuation followed by lowercase continues the sentence`() {
        val text = words("\u201CWait!\u201D she said.")
        assertEquals(Pause.CLAUSE, text[0].pause)
        assertEquals(Pause.PARAGRAPH, text[2].pause)
    }

    @Test
    fun `closing quotes after terminal punctuation still end the sentence`() {
        val text = words("He said \u201CGo.\u201D Then he left.")
        assertEquals(Pause.SENTENCE, text.first { it.text == "\u201CGo.\u201D" }.pause)
    }

    @Test
    fun `dash-joined words are split after the dash`() {
        assertEquals(listOf("word\u2014", "another"), words("word\u2014another").map { it.text })
        assertEquals(listOf("this\u2014", "that"), words("this--that").map { it.text })
        assertEquals(listOf("\u2014quote"), words("\u2014quote").map { it.text })
        assertEquals(Pause.CLAUSE, words("one\u2014two")[0].pause)
    }

    @Test
    fun `records letter counts and digits`() {
        val text = words("In 1984, extraordinary")
        assertTrue(text[1].hasDigits)
        assertFalse(text[0].hasDigits)
        assertEquals(13, text[2].letterCount)
    }

    @Test
    fun `indexes sentences and paragraphs`() {
        val text = Tokenizer.tokenize(listOf("One two. Three four five.", "", "Six seven."))
        // One0 two.1 | Three2 four3 five.4 || Six5 seven.6
        assertEquals(7, text.size)
        assertEquals(3, text.sentenceCount)
        assertEquals(2, text.sentenceStartOf(4))
        assertEquals(5, text.nextSentenceStart(3))
        assertNull(text.nextSentenceStart(6))
        assertEquals(5, text.paragraphStartOf(6))
        assertEquals(5, text.paragraphEndOf(0))
        assertEquals(Pause.PARAGRAPH, text[4].pause)
    }

    @Test
    fun `countWords matches tokenize`() {
        val paragraphs = listOf("A short\u2014but dense paragraph.", "Another one, here!")
        assertEquals(Tokenizer.tokenize(paragraphs).size, Tokenizer.countWords(paragraphs))
    }

    @Test
    fun `context window is limited to the current paragraph`() {
        val text = Tokenizer.tokenize(listOf("a b c d e f g", "h i j"))
        val context = text.contextAround(index = 3, before = 2, after = 1)
        assertEquals(listOf("b", "c", "d", "e"), context.words)
        assertEquals(2, context.currentIndex)
        assertTrue(context.truncatedStart)
        assertTrue(context.truncatedEnd)

        val tail = text.contextAround(index = 8)
        assertEquals(listOf("h", "i", "j"), tail.words)
        assertEquals(1, tail.currentIndex)
        assertFalse(tail.truncatedStart)
    }

    @Test
    fun `empty input produces empty text`() {
        val text = Tokenizer.tokenize(listOf("", "   "))
        assertTrue(text.isEmpty())
        assertEquals(0, text.sentenceStartOf(0))
        assertNull(text.nextSentenceStart(0))
    }

    @Test
    fun `paragraph ranges cover every word in order`() {
        val text = Tokenizer.tokenize(listOf("One two.", "Three four five.", "Six."))
        assertEquals(listOf(0..1, 2..4, 5..5), text.paragraphRanges())
    }
}
