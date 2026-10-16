package io.github.fianna76.speedread.data.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfTextCleanerTest {

    private val mark = PdfTextCleaner.PARAGRAPH_MARK

    @Test
    fun `joins wrapped lines and splits on paragraph marks`() {
        val raw = "It was a dark\nand stormy night.${mark}The next\nmorning came.$mark"
        assertEquals(
            listOf("It was a dark and stormy night.", "The next morning came."),
            PdfTextCleaner.paragraphs(raw),
        )
    }

    @Test
    fun `undoes end-of-line hyphenation only before lowercase continuations`() {
        val raw = "speed reading improves compre-\nhension and the well-\nKnown effect"
        assertEquals(
            listOf("speed reading improves comprehension and the well- Known effect"),
            PdfTextCleaner.paragraphs(raw),
        )
    }

    @Test
    fun `drops page-number lines and soft hyphens`() {
        val raw = "12\nFirst para\u00ADgraph text.\nPage 3\n4 of 20\n\nSecond paragraph."
        assertEquals(listOf("First paragraph text.", "Second paragraph."), PdfTextCleaner.paragraphs(raw))
    }

    @Test
    fun `blank input yields no paragraphs`() {
        assertEquals(emptyList<String>(), PdfTextCleaner.paragraphs("  \n$mark\n  \n"))
    }
}
