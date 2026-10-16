package io.github.fianna76.speedread.data.importing

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.graphics.scale
import io.github.fianna76.speedread.data.BookFileStore
import io.github.fianna76.speedread.data.db.BookDao
import io.github.fianna76.speedread.data.db.BookEntity
import io.github.fianna76.speedread.data.db.ChapterEntity
import io.github.fianna76.speedread.epub.EpubArchive
import io.github.fianna76.speedread.epub.EpubBook
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
 * Imports an EPUB from a content [Uri] into the library:
 *
 * 1. copy it into app-private storage while hashing it (SHA-256) and enforcing a size limit;
 * 2. skip it if a book with the same hash is already in the library;
 * 3. parse the package, extract every chapter's text to count words and pick chapter titles;
 * 4. store a down-scaled cover thumbnail;
 * 5. insert the book and its chapters in one transaction.
 */
class BookImporter(
    private val contentResolver: ContentResolver,
    private val files: BookFileStore,
    private val dao: BookDao,
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

            val analysis = EpubArchive.open(temp).use { archive -> analyze(archive) }
            if (analysis.chapters.isEmpty()) return@withContext ImportResult.Failed(displayName, ImportError.NO_TEXT)

            val bookFileName = "$hash.epub"
            val target = files.bookFile(bookFileName)
            if (!temp.renameTo(target)) temp.copyTo(target, overwrite = true)
            storedBook = bookFileName
            storedCover = analysis.coverBytes?.let { bytes -> "$hash.jpg".takeIf { saveCover(bytes, files.coverFile(it)) } }

            val metadata = analysis.book.metadata
            val title = metadata.title?.takeIf { it.isNotBlank() }
                ?: displayName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
                ?: UNTITLED
            val entity = BookEntity(
                title = title,
                author = metadata.authors.joinToString(", ").ifBlank { null },
                language = metadata.language,
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

    private suspend fun analyze(archive: EpubArchive): Analysis {
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
        return Analysis(book, chapters, coverBytes)
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
            files.ensureDirectories()
            target.outputStream().use { thumbnail.compress(Bitmap.CompressFormat.JPEG, COVER_JPEG_QUALITY, it) }
        } catch (e: IOException) {
            Log.w(TAG, "Could not store cover", e)
            false
        } finally {
            if (thumbnail !== decoded) thumbnail.recycle()
            decoded.recycle()
        }
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment

    private fun EpubException.toImportError(): ImportError = when (this) {
        is EpubException.DrmProtected -> ImportError.DRM_PROTECTED
        is EpubException.TooLarge -> ImportError.TOO_LARGE
        is EpubException.NotAnEpub, is EpubException.Malformed -> ImportError.NOT_AN_EPUB
    }

    private class Analysis(val book: EpubBook, val chapters: List<ChapterEntity>, val coverBytes: ByteArray?)

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
