package io.github.fianna76.studypack.data.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

/**
 * Renders pages of a PDF to bitmaps with the platform's [PdfRenderer]. The renderer allows one
 * open page at a time and is not thread-safe, so rendering is serialised here.
 */
class PdfPageRenderer private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : Closeable {

    private val mutex = Mutex()
    private var closed = false

    val pageCount: Int = renderer.pageCount

    /** Height / width of each page, measured once when the document is opened. */
    val aspectRatios: List<Float> = (0 until pageCount).map { index ->
        renderer.openPage(index).use { page -> if (page.width > 0) page.height.toFloat() / page.width else SQRT2 }
    }

    /** Renders page [index] [widthPx] pixels wide on a white background; `null` once closed. */
    suspend fun render(index: Int, widthPx: Int): Bitmap? = mutex.withLock {
        if (closed || index !in 0 until pageCount || widthPx <= 0) return@withLock null
        withContext(Dispatchers.IO) {
            renderer.openPage(index).use { page ->
                val height = (widthPx * aspectRatios[index]).toInt().coerceAtLeast(1)
                val bitmap = createBitmap(widthPx, height)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

    override fun close() {
        // Wait for a render in progress; closing under it would crash the renderer.
        kotlinx.coroutines.runBlocking {
            mutex.withLock {
                if (closed) return@withLock
                closed = true
                renderer.close()
                descriptor.close()
            }
        }
    }

    companion object {
        private const val SQRT2 = 1.4142135f

        /** Opens [file]; throws if the platform cannot read it. */
        fun open(file: File): PdfPageRenderer {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            return try {
                PdfPageRenderer(descriptor, PdfRenderer(descriptor))
            } catch (e: Exception) {
                descriptor.close()
                throw e
            }
        }
    }
}
