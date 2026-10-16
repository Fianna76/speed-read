package io.github.fianna76.speedread.testing

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import java.io.File

/** Builds small PDFs with PdfBox so tests don't depend on binary fixtures. */
object TestPdfs {

    /**
     * Writes a PDF with one page per entry of [pages], each page holding the given lines.
     * [outline] adds top-level bookmarks as (title, zero-based page index).
     */
    fun write(
        context: Context,
        target: File,
        pages: List<List<String>>,
        title: String? = null,
        author: String? = null,
        outline: List<Pair<String, Int>> = emptyList(),
        userPassword: String? = null,
    ): File {
        PDFBoxResourceLoader.init(context)
        PDDocument().use { document ->
            for (lines in pages) {
                val page = PDPage()
                document.addPage(page)
                if (lines.isEmpty()) continue
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.setLeading(16f)
                    stream.newLineAtOffset(72f, 720f)
                    for (line in lines) {
                        stream.showText(line)
                        stream.newLine()
                    }
                    stream.endText()
                }
            }
            document.documentInformation.title = title
            document.documentInformation.author = author
            if (outline.isNotEmpty()) {
                val root = PDDocumentOutline()
                for ((label, pageIndex) in outline) {
                    root.addLast(PDOutlineItem().apply {
                        this.title = label
                        setDestination(document.getPage(pageIndex))
                    })
                }
                document.documentCatalog.documentOutline = root
            }
            if (userPassword != null) {
                document.protect(StandardProtectionPolicy("owner-secret", userPassword, AccessPermission()))
            }
            document.save(target)
        }
        return target
    }
}
