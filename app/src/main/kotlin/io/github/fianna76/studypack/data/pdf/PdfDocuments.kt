package io.github.fianna76.studypack.data.pdf

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.IOException

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

    /** Paragraphs of text, page by page (one list per page, possibly empty). */
    fun pageParagraphs(file: File): List<List<String>> = open(file).use { document ->
        val stripper = PDFTextStripper().apply {
            paragraphStart = ""
            paragraphEnd = PdfTextCleaner.PARAGRAPH_MARK.toString()
            lineSeparator = "\n"
            pageEnd = "\n"
        }
        (1..document.numberOfPages).map { page ->
            stripper.startPage = page
            stripper.endPage = page
            PdfTextCleaner.paragraphs(stripper.getText(document))
        }
    }

    private fun open(file: File): PDDocument = try {
        PDDocument.load(file, MemoryUsageSetting.setupMixed(MAX_MAIN_MEMORY_BYTES))
    } catch (e: InvalidPasswordException) {
        throw PdfException("This PDF is password-protected. Save an unprotected copy and import that.", e)
    } catch (e: IOException) {
        throw PdfException("This file is not a readable PDF.", e)
    }

    private companion object {
        const val MAX_MAIN_MEMORY_BYTES = 32L * 1024 * 1024
    }
}
