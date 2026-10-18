package io.github.fianna76.speedread.ui.reader

import io.github.fianna76.speedread.data.Bookmark
import io.github.fianna76.speedread.data.ReadingPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class NotesExportTest {

    @Test
    fun `notes are grouped by chapter in reading order`() {
        val marks = listOf(
            Bookmark(1, ReadingPosition(2, 5), "A later place", 0, highlight = "It was the best of times.", note = "Famous opening"),
            Bookmark(2, ReadingPosition(0, 9), "Where I stopped", 0),
            Bookmark(3, ReadingPosition(2, 1), "Earlier", 0, highlight = "Call me Ishmael."),
        )
        val markdown = NotesExport.markdown("A Book", marks) { "Chapter ${it + 1}" }
        assertEquals(
            """
            # A Book

            ## Chapter 1

            - Bookmark: Where I stopped…

            ## Chapter 3

            > Call me Ishmael.

            > It was the best of times.

            Famous opening

            """.trimIndent(),
            markdown,
        )
    }
}
