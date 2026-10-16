package io.github.fianna76.speedread.data.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import java.io.File

/** Renders a PDF's first page for use as a cover. */
fun interface PdfCoverRenderer {
    /** Returns the first page scaled to [height] pixels, or `null` if the PDF has no pages. */
    fun renderFirstPage(pdf: File, height: Int): Bitmap?
}

/** [PdfCoverRenderer] backed by the platform's [PdfRenderer] (fast, native, no extra code). */
object PlatformPdfCoverRenderer : PdfCoverRenderer {

    override fun renderFirstPage(pdf: File, height: Int): Bitmap? =
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            val renderer = PdfRenderer(descriptor)
            try {
                if (renderer.pageCount == 0) return null
                val page = renderer.openPage(0)
                try {
                    val width = (page.width.toLong() * height / page.height.coerceAtLeast(1)).toInt()
                        .coerceIn(1, height * 2)
                    createBitmap(width, height).apply {
                        eraseColor(Color.WHITE) // Pages are transparent where nothing is drawn.
                        page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                } finally {
                    page.close()
                }
            } finally {
                renderer.close()
            }
        }
}
