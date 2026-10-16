package io.github.fianna76.speedread.data.pdf

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.Closeable
import java.io.File

/** A range of PDF pages, `[start, endExclusive)`, zero-based. Stored as a chapter's href. */
data class PdfPageRange(val start: Int, val endExclusive: Int) {
    init {
        require(start in 0 until endExclusive) { "Invalid page range $start-$endExclusive" }
    }

    val pageCount: Int get() = endExclusive - start

    fun toHref(): String = "$HREF_PREFIX$start-$endExclusive"

    companion object {
        private const val HREF_PREFIX = "pages:"

        /** Parses an href written by [toHref]; `null` for anything else (e.g. EPUB paths). */
        fun fromHref(href: String): PdfPageRange? {
            if (!href.startsWith(HREF_PREFIX)) return null
            val (start, end) = href.removePrefix(HREF_PREFIX).split('-').mapNotNull { it.toIntOrNull() }
                .takeIf { it.size == 2 } ?: return null
            return if (start in 0 until end) PdfPageRange(start, end) else null
        }
    }
}

/** A readable section of a PDF: an outline (bookmark) entry or a block of pages. */
data class PdfSection(val title: String?, val pages: PdfPageRange)

/**
 * Opens PDF books for text extraction with PdfBox-Android. Large documents spill to temporary
 * files instead of being held entirely in memory.
 */
class PdfBooks(context: Context) {

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    /** Throws [com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException] for locked files. */
    fun open(file: File): PdfBook = PdfBook(PDDocument.load(file, MemoryUsageSetting.setupMixed(MAX_MAIN_MEMORY_BYTES)))

    private companion object {
        const val MAX_MAIN_MEMORY_BYTES = 32L * 1024 * 1024
    }
}

/** An open PDF document. Not thread-safe; close it when done. */
class PdfBook internal constructor(private val document: PDDocument) : Closeable {

    val pageCount: Int get() = document.numberOfPages

    val title: String? get() = document.documentInformation?.title?.trim()?.ifEmpty { null }

    val author: String? get() = document.documentInformation?.author?.trim()?.ifEmpty { null }

    /**
     * Reading sections. Top-level outline entries become chapters when the PDF has at least two;
     * otherwise, and for very long chapters, pages are grouped into blocks of [PAGES_PER_SECTION]
     * so extraction while reading stays quick.
     */
    fun sections(): List<PdfSection> {
        val pages = pageCount
        if (pages == 0) return emptyList()
        val marks = outlineMarks()
        val chapters = if (marks.size < 2) {
            listOf(PdfSection(null, PdfPageRange(0, pages)))
        } else {
            val starts = if (marks.first().first > 0) listOf(0 to null) + marks else marks
            starts.mapIndexed { i, (start, title) ->
                val end = starts.getOrNull(i + 1)?.first ?: pages
                PdfSection(title, PdfPageRange(start, end))
            }
        }
        return chapters.flatMap(::splitLongSection)
    }

    /** Paragraphs of text on [range]'s pages. */
    fun paragraphs(range: PdfPageRange): List<String> {
        val stripper = PDFTextStripper().apply {
            startPage = range.start + 1 // PDFTextStripper pages are one-based and inclusive.
            endPage = range.endExclusive
            paragraphStart = ""
            paragraphEnd = PdfTextCleaner.PARAGRAPH_MARK.toString()
            lineSeparator = "\n"
            pageEnd = "\n"
        }
        return PdfTextCleaner.paragraphs(stripper.getText(document))
    }

    override fun close() = document.close()

    /**
     * (page index, title) of top-level outline entries in page order. Sections are whole pages,
     * so entries starting on the same page share one section and their titles are combined.
     */
    private fun outlineMarks(): List<Pair<Int, String?>> = runCatching {
        val outline = document.documentCatalog.documentOutline ?: return emptyList()
        outline.children().mapNotNull { item ->
            val page = item.findDestinationPage(document) ?: return@mapNotNull null
            val index = document.pages.indexOf(page)
            if (index < 0) null else index to item.title?.trim()?.ifEmpty { null }
        }
    }.getOrDefault(emptyList())
        .groupBy({ it.first }, { it.second })
        .toSortedMap()
        .map { (page, titles) -> page to titles.filterNotNull().joinToString(" \u00B7 ").ifEmpty { null } }

    private fun splitLongSection(section: PdfSection): List<PdfSection> {
        val range = section.pages
        if (range.pageCount <= MAX_SECTION_PAGES) return listOf(section)
        return (range.start until range.endExclusive step PAGES_PER_SECTION).mapIndexed { i, start ->
            PdfSection(
                title = section.title.takeIf { i == 0 },
                pages = PdfPageRange(start, minOf(start + PAGES_PER_SECTION, range.endExclusive)),
            )
        }
    }

    private companion object {
        const val PAGES_PER_SECTION = 10
        const val MAX_SECTION_PAGES = 30
    }
}
