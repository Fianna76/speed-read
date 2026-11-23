package io.github.fianna76.studypack.data.pdf

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import io.github.fianna76.studypack.testing.TestPdfs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/** Section detection and reading order on real PDFs, through PdfBox's text extraction. */
@RunWith(AndroidJUnit4::class)
class NotesBookTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val pdfs by lazy { PdfDocuments(context) }

    private fun sample(): NotesBook {
        val file = File(tmp.root, "sample.pdf")
        context.assets.open("sample/DEMO101-circuits-overview.pdf").use { input -> file.outputStream().use { input.copyTo(it) } }
        return NotesBook.from(pdfs.extract(file))
    }

    @Test
    fun bundledSampleSkipsTitlePageContentsAndSources() {
        val book = sample()
        assertEquals(
            listOf(null, "1. Ohm's law and power", "2. Series and parallel", "3. RC circuits", "4. Op-amps", "5. Formula sheet", "Sources"),
            book.chapters.map { it.title },
        )
        assertEquals(
            listOf(SectionKind.FRONT, SectionKind.BODY, SectionKind.BODY, SectionKind.BODY, SectionKind.BODY, SectionKind.BODY, SectionKind.BACK),
            book.chapters.map { it.kind },
        )
        // Reading starts at section 1, which opens with its heading.
        assertEquals(NotesPosition(1, 0), book.start)
        assertEquals("1.", book.chapters[1].text[0].text)
        // The front matter really is the title page and contents.
        assertEquals(0, book.chapters[0].firstPage)
        assertEquals(1, book.chapters[0].lastPage)
    }

    @Test
    fun readingOrderSkipsFrontAndBackMatter() {
        val book = sample()
        assertEquals(2, book.nextChapterAfter(1))
        assertEquals("After the formula sheet comes Sources, which is not read on into", null, book.nextChapterAfter(5))
        assertEquals("The title page continues with section 1", 1, book.nextChapterAfter(0))
        assertNull(book.nextChapterAfter(6))
    }

    @Test
    fun pagesMapToSections() {
        val book = sample()
        // Pages in the front matter start at section 1.
        assertEquals(book.start, book.positionForPage(0))
        assertEquals(book.start, book.positionForPage(1))
        assertEquals(NotesPosition(3, 0), book.positionForPage(4))
        assertEquals(4, book.chapters[3].pageOf(5))
        // Absolute word positions round-trip.
        val position = NotesPosition(3, 7)
        assertEquals(position, book.locate(book.absolute(position)))
        assertNull(book.locate(book.totalWords))
    }

    @Test
    fun generatedOverviewWithoutBookmarks() {
        // The skill's layout, drawn with plain PdfBox text (no outline, no font sizes to go by).
        val pages = listOf(
            listOf("EE5105 · Applied Circuit Design", "", "ECG Amplifier: Single-Supply Bench Demo", "", "Prepared 18 Nov 2026 · Sources: handout"),
            listOf("Contents", "", "1. The big picture ........ 3", "2. CMRR ........ 4", "3. Formula sheet ........ 5", "Sources ........ 5"),
            listOf("1. The big picture", "", "The amplifier is a three-op-amp instrumentation amplifier.", "", "Its gain is about 130."),
            listOf("2. CMRR", "", "CMRR peaks near five hertz because of three mechanisms."),
            listOf("3. Formula sheet", "", "G = 1 + 2R2/R1", "", "", "Sources", "", "Annotated design reference."),
        )
        val file = TestPdfs.write(context, File(tmp.root, "EE5105-ecg-amp-overview.pdf"), pages)
        val book = NotesBook.from(pdfs.extract(file))
        assertEquals(listOf(null, "1. The big picture", "2. CMRR", "3. Formula sheet", "Sources"), book.chapters.map { it.title })
        assertEquals(SectionKind.BACK, book.chapters.last().kind)
        assertTrue(book.chapters[3].text.words.none { it.text == "Annotated" })
        assertEquals(1, book.start.chapter)
    }

    @Test
    fun plainPdfWithoutStructureIsReadFromTheStart() {
        val pages = List(3) { i -> listOf("Plain page ${i + 1} with an ordinary sentence of text on it for reading.") + List(20) { "More words to read on page ${i + 1}." } }
        val file = TestPdfs.write(context, File(tmp.root, "plain.pdf"), pages)
        val book = NotesBook.from(pdfs.extract(file))
        assertEquals(listOf(SectionKind.BODY), book.chapters.map { it.kind })
        assertEquals(NotesPosition(0, 0), book.start)
        assertEquals(2, book.chapters[0].pageOf(book.chapters[0].wordCount - 1))
    }
}
