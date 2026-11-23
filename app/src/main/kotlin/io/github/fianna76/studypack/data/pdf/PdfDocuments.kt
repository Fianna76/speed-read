package io.github.fianna76.studypack.data.pdf

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException

/** One page's text: its lines as laid out, and the same text joined into paragraphs. */
@Serializable
data class PageText(val lines: List<String>, val paragraphs: List<String>)

/** A top-level bookmark. */
@Serializable
data class OutlineMark(val page: Int, val title: String)

@Serializable
data class ExtractedPdf(val pages: List<PageText>, val outline: List<OutlineMark>)

/** What import needs to know about a PDF. */
data class PdfInfo(val pageCount: Int, val title: String?)

/** A PDF that cannot be used, with a message for the user. */
class PdfException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Reads PDFs with PdfBox-Android: checks them on import and extracts their text for speed
 * reading. (The viewer renders pages with the platform's PdfRenderer instead.) Large documents
 * spill to temporary files rather than being held entirely in memory.
 */
class PdfDocuments(context: Context) {

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    /** Page count and title; throws [PdfException] for broken, empty or password-protected files. */
    fun inspect(file: File): PdfInfo = open(file).use { document ->
        val pages = document.numberOfPages
        if (pages <= 0) throw PdfException("This PDF has no pages.")
        PdfInfo(pages, document.documentInformation?.title?.trim()?.ifEmpty { null })
    }

    /**
     * The text of every page, as raw lines (to find headings and the contents page) and as
     * cleaned paragraphs (to read), plus the top-level bookmarks.
     */
    fun extract(file: File): ExtractedPdf = open(file).use { document ->
        val stripper = PDFTextStripper().apply {
            paragraphStart = ""
            paragraphEnd = PdfTextCleaner.PARAGRAPH_MARK.toString()
            lineSeparator = "\n"
            pageEnd = "\n"
        }
        val pages = (1..document.numberOfPages).map { page ->
            stripper.startPage = page
            stripper.endPage = page
            val raw = stripper.getText(document)
            val lines = raw.split('\n', PdfTextCleaner.PARAGRAPH_MARK)
                .map { it.replace("\u00AD", "").replace(WHITESPACE, " ").trim() }
                .filter { it.isNotEmpty() }
            PageText(lines, PdfTextCleaner.paragraphs(raw))
        }
        ExtractedPdf(pages, outline(document))
    }

    /** (zero-based page, title) of the top-level bookmarks, in page order. */
    private fun outline(document: PDDocument): List<OutlineMark> = runCatching {
        val outline = document.documentCatalog.documentOutline ?: return emptyList()
        outline.children().mapNotNull { item ->
            val page = item.findDestinationPage(document) ?: return@mapNotNull null
            val index = document.pages.indexOf(page)
            val title = item.title?.trim()?.ifEmpty { null }
            if (index < 0 || title == null) null else OutlineMark(index, title)
        }.sortedBy { it.page }
    }.getOrDefault(emptyList())

    private fun open(file: File): PDDocument = try {
        PDDocument.load(file, MemoryUsageSetting.setupMixed(MAX_MAIN_MEMORY_BYTES))
    } catch (e: InvalidPasswordException) {
        throw PdfException("This PDF is password-protected. Save an unprotected copy and import that.", e)
    } catch (e: IOException) {
        throw PdfException("This file is not a readable PDF.", e)
    }

    private companion object {
        const val MAX_MAIN_MEMORY_BYTES = 32L * 1024 * 1024
        val WHITESPACE = Regex("[\\s\\p{Z}]+")
    }
}
