package io.github.fianna76.speedread.data.importing

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.graphics.scale
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import io.github.fianna76.speedread.data.BookFileStore
import io.github.fianna76.speedread.data.BookFormat
import io.github.fianna76.speedread.data.db.BookDao
import io.github.fianna76.speedread.data.db.BookEntity
import io.github.fianna76.speedread.data.db.ChapterEntity
import io.github.fianna76.speedread.data.pdf.PdfBook
import io.github.fianna76.speedread.data.pdf.PdfBooks
import io.github.fianna76.speedread.data.pdf.PdfCoverRenderer
import io.github.fianna76.speedread.data.pdf.PlatformPdfCoverRenderer
import io.github.fianna76.speedread.epub.EpubArchive
import io.github.fianna76.speedread.epub.EpubException
import io.github.fianna76.speedread.epub.EpubParser
import io.github.fianna76.speedread.epub.XhtmlTextExtractor
import io.github.fianna76.speedread.rsvp.Tokenizer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest

/**
 * Imports an EPUB or PDF from a content [Uri] into the library:
 *
 * 1. copy it into app-private storage while hashing it (SHA-256) and enforcing a size limit;
 * 2. skip it if a book with the same hash is already in the library;
 * 3. identify the format from the file's first bytes;
 * 4. split it into chapters (EPUB spine + TOC, or PDF outline / page blocks) and extract every
 *    chapter's text to count words;
 * 5. store a down-scaled cover thumbnail (EPUB cover image, or the PDF's first page);
 * 6. insert the book and its chapters in one transaction.
 */
class BookImporter(
    private val contentResolver: ContentResolver,
    private val files: BookFileStore,
    private val dao: BookDao,
    private val pdfBooks: PdfBooks,
    private val pdfCovers: PdfCoverRenderer = PlatformPdfCoverRenderer,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun import(uri: Uri): ImportResult = withContext(ioDispatcher) {
        val displayName = queryDisplayName(uri)
        val temp = files.createTempFile()
        var storedBook: String? = null
        var storedCover: String? = null
        try {
            val hash = copyAndHash(uri, temp)
            dao.findByHash(hash)?.let { return@withContext ImportResult.AlreadyInLibrary(it.id, it.title) }

            val format = BookFormat.sniff(temp)
                ?: return@withContext ImportResult.Failed(displayName, ImportError.INVALID_FILE)
            val bookFileName = "$hash.${format.extension}"
            storedBook = bookFileName // From here on, a failure rolls back the book's files.
            val analysis = when (format) {
                BookFormat.EPUB -> EpubArchive.open(temp).use { archive -> analyzeEpub(archive) }
                BookFormat.PDF -> openPdf(temp).use { pdf -> analyzePdf(pdf, bookFileName) }
            }
            if (analysis.chapters.isEmpty()) return@withContext ImportResult.Failed(displayName, ImportError.NO_TEXT)

            val target = files.bookFile(bookFileName)
            files.ensureDirectories()
            if (!temp.renameTo(target)) temp.copyTo(target, overwrite = true)
            val coverFileName = "$hash.jpg"
            val coverSaved = when (format) {
                BookFormat.EPUB -> analysis.coverBytes?.let { saveCover(it, files.coverFile(coverFileName)) } == true
                BookFormat.PDF -> renderPdfCover(target, files.coverFile(coverFileName))
            }
            storedCover = coverFileName.takeIf { coverSaved }

            val title = analysis.title?.takeIf { it.isNotBlank() }
                ?: displayName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
                ?: UNTITLED
            val entity = BookEntity(
                title = title,
                author = analysis.author,
                language = analysis.language,
                format = format.name,
                fileName = bookFileName,
                coverFileName = storedCover,
                contentHash = hash,
                totalWords = analysis.chapters.sumOf { it.wordCount },
                addedAt = clock(),
            )
            val id = dao.insertBookWithChapters(entity, analysis.chapters)
            storedBook = null // Committed: keep the files.
            storedCover = null
            ImportResult.Imported(id, title)
        } catch (e: EpubException) {
            Log.w(TAG, "Rejected $displayName", e)
            ImportResult.Failed(displayName, e.toImportError())
        } catch (e: InvalidPasswordException) {
            ImportResult.Failed(displayName, ImportError.PASSWORD_PROTECTED)
        } catch (e: InvalidBookException) {
            Log.w(TAG, "Rejected $displayName", e)
            ImportResult.Failed(displayName, ImportError.INVALID_FILE)
        } catch (e: BookTooLargeException) {
            ImportResult.Failed(displayName, ImportError.TOO_LARGE)
        } catch (e: IOException) {
            Log.w(TAG, "Could not read $displayName", e)
            ImportResult.Failed(displayName, ImportError.UNREADABLE)
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to read $uri", e)
            ImportResult.Failed(displayName, ImportError.UNREADABLE)
        } finally {
            temp.delete()
            // Roll back files of an import that failed before its database row was committed.
            files.delete(storedBook, storedCover)
        }
    }

    private suspend fun copyAndHash(uri: Uri, target: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = contentResolver.openInputStream(uri) ?: throw FileNotFoundException("Cannot open $uri")
        input.use { source ->
            target.outputStream().use { sink ->
                val buffer = ByteArray(BUFFER_SIZE)
                var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = source.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_BOOK_BYTES) throw BookTooLargeException()
                    digest.update(buffer, 0, read)
                    sink.write(buffer, 0, read)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun analyzeEpub(archive: EpubArchive): Analysis {
        val book = EpubParser().parse(archive)
        val chapters = mutableListOf<ChapterEntity>()
        var startWord = 0
        for (item in book.readingOrder) {
            currentCoroutineContext().ensureActive()
            // A single broken document shouldn't sink the whole book; it is simply skipped.
            val extracted = runCatching { XhtmlTextExtractor.extract(archive.readBytes(item.href)) }
                .onFailure { Log.w(TAG, "Skipping unreadable section ${item.href}", it) }
                .getOrNull() ?: continue
            val wordCount = Tokenizer.countWords(extracted.paragraphs)
            if (wordCount == 0) continue
            chapters += ChapterEntity(
                bookId = 0,
                index = chapters.size,
                title = (book.tocTitleFor(item.href) ?: extracted.firstHeading)?.take(MAX_TITLE_LENGTH),
                href = item.href,
                wordCount = wordCount,
                startWord = startWord,
            )
            startWord += wordCount
        }
        val coverBytes = book.coverImageHref?.let { href ->
            runCatching { archive.readBytes(href, limit = MAX_COVER_BYTES) }.getOrNull()
        }
        val metadata = book.metadata
        return Analysis(
            title = metadata.title,
            author = metadata.authors.joinToString(", ").ifBlank { null },
            language = metadata.language,
            chapters = chapters,
            coverBytes = coverBytes,
        )
    }

    /** Opens a PDF, telling a damaged or fake file apart from a password-protected one. */
    private fun openPdf(file: File): PdfBook = try {
        pdfBooks.open(file)
    } catch (e: InvalidPasswordException) {
        throw e
    } catch (e: IOException) {
        throw InvalidBookException(e)
    }

    /** Splits [pdf] into sections and caches their text for reading, see [BookFileStore.readChapterText]. */
    private suspend fun analyzePdf(pdf: PdfBook, bookFileName: String): Analysis {
        val chapters = mutableListOf<ChapterEntity>()
        var startWord = 0
        for (section in pdf.sections()) {
            currentCoroutineContext().ensureActive()
            val paragraphs = runCatching { pdf.paragraphs(section.pages) }
                .onFailure { Log.w(TAG, "Skipping unreadable pages ${section.pages}", it) }
                .getOrNull() ?: continue
            // Scanned pages have no text layer; such sections are simply skipped.
            val wordCount = Tokenizer.countWords(paragraphs)
            if (wordCount == 0) continue
            val href = section.pages.toHref()
            files.writeChapterText(bookFileName, href, paragraphs)
            chapters += ChapterEntity(
                bookId = 0,
                index = chapters.size,
                title = section.title?.take(MAX_TITLE_LENGTH),
                href = href,
                wordCount = wordCount,
                startWord = startWord,
            )
            startWord += wordCount
        }
        return Analysis(title = pdf.title, author = pdf.author, language = null, chapters = chapters, coverBytes = null)
    }

    /** Stores the PDF's first page, rendered by [pdfCovers], as the cover thumbnail. */
    private fun renderPdfCover(pdf: File, target: File): Boolean {
        val bitmap = try {
            pdfCovers.renderFirstPage(pdf, COVER_HEIGHT_PX)
        } catch (e: Exception) {
            // Covers are cosmetic: the platform renderer rejects some files PdfBox reads fine.
            Log.w(TAG, "Could not render PDF cover", e)
            null
        } ?: return false
        return try {
            saveBitmap(bitmap, target)
        } finally {
            bitmap.recycle()
        }
    }

    /** Decodes [bytes] at a reduced size and stores a JPEG thumbnail. Returns `false` if undecodable. */
    private fun saveCover(bytes: ByteArray, target: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

        var sampleSize = 1
        while (bounds.outHeight / (sampleSize * 2) >= COVER_HEIGHT_PX) sampleSize *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize })
            ?: return false
        val thumbnail = if (decoded.height > COVER_HEIGHT_PX) {
            val width = (decoded.width.toLong() * COVER_HEIGHT_PX / decoded.height).toInt().coerceAtLeast(1)
            decoded.scale(width, COVER_HEIGHT_PX)
        } else {
            decoded
        }
        return try {
            saveBitmap(thumbnail, target)
        } finally {
            if (thumbnail !== decoded) thumbnail.recycle()
            decoded.recycle()
        }
    }

    private fun saveBitmap(bitmap: Bitmap, target: File): Boolean = try {
        files.ensureDirectories()
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, COVER_JPEG_QUALITY, it) }
    } catch (e: IOException) {
        Log.w(TAG, "Could not store cover", e)
        false
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.takeIf { '.' in it } // Skip bare IDs like ".../file/14".

    private fun EpubException.toImportError(): ImportError = when (this) {
        is EpubException.DrmProtected -> ImportError.DRM_PROTECTED
        is EpubException.TooLarge -> ImportError.TOO_LARGE
        is EpubException.NotAnEpub, is EpubException.Malformed -> ImportError.INVALID_FILE
    }

    /** What an import learned about a book, independent of its format. */
    private class Analysis(
        val title: String?,
        val author: String?,
        val language: String?,
        val chapters: List<ChapterEntity>,
        /** EPUB cover image bytes; PDFs render their first page instead. */
        val coverBytes: ByteArray?,
    )

    private class InvalidBookException(cause: Throwable) : Exception(cause)

    private class BookTooLargeException : Exception()

    private companion object {
        const val TAG = "BookImporter"
        const val UNTITLED = "Untitled"
        const val BUFFER_SIZE = 64 * 1024
        const val MAX_BOOK_BYTES = 256L * 1024 * 1024
        const val MAX_COVER_BYTES = 20L * 1024 * 1024
        const val MAX_TITLE_LENGTH = 120
        const val COVER_HEIGHT_PX = 600
        const val COVER_JPEG_QUALITY = 85
    }
}
