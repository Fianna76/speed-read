package io.github.fianna76.speedread.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BookFormatTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(bytes: ByteArray) = tmp.newFile().apply { writeBytes(bytes) }

    @Test
    fun `identifies formats by content, not name`() {
        assertEquals(BookFormat.EPUB, BookFormat.sniff(file(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14))))
        assertEquals(BookFormat.PDF, BookFormat.sniff(file("%PDF-1.7\n...".toByteArray())))
        // Some producers put junk before the PDF header.
        assertEquals(BookFormat.PDF, BookFormat.sniff(file("\u0000\u0000junk%PDF-1.4".toByteArray())))
    }

    @Test
    fun `rejects other files`() {
        assertNull(BookFormat.sniff(file("<html>not a book</html>".toByteArray())))
        assertNull(BookFormat.sniff(file(ByteArray(0))))
    }

    @Test
    fun `stored names fall back to EPUB`() {
        assertEquals(BookFormat.PDF, BookFormat.fromStored("PDF"))
        assertEquals(BookFormat.EPUB, BookFormat.fromStored("SOMETHING_ELSE"))
    }
}
