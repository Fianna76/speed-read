package io.github.fianna76.speedread.rsvp

import org.junit.Assert.assertEquals
import org.junit.Test

class ChunkingTest {

    // in0 the1 morning,2 she3 walked4 to5 extraordinarily6 beautiful7 places.8
    private val text = Tokenizer.tokenize(listOf("In the morning, she walked to extraordinarily beautiful places."))

    @Test
    fun `short words are grouped up to the chunk size`() {
        assertEquals(3, Chunking.span(text, 0, chunkSize = 3)) // "In the morning,"
        assertEquals(2, Chunking.span(text, 0, chunkSize = 2))
        assertEquals(1, Chunking.span(text, 0, chunkSize = 1))
    }

    @Test
    fun `chunks never cross a pause, and long words stand alone`() {
        assertEquals(1, Chunking.span(text, 2, chunkSize = 3)) // "morning," ends a clause.
        assertEquals(3, Chunking.span(text, 3, chunkSize = 3)) // "she walked to"
        assertEquals(1, Chunking.span(text, 6, chunkSize = 3)) // "extraordinarily" + "beautiful" is too long.
        assertEquals(1, Chunking.span(text, 8, chunkSize = 3)) // Last word.
    }

    @Test
    fun `a chunk is shown as one phrase focused near its middle`() {
        val chunk = Chunking.join(text, 3, 3)
        assertEquals("she walked to", chunk.text)
        assertEquals('a', chunk.text[chunk.orpStart]) // Letter 5 of 11 (40 %).
        assertEquals(11, chunk.letterCount)
        assertEquals(text[3], Chunking.join(text, 3, 1))
    }
}
