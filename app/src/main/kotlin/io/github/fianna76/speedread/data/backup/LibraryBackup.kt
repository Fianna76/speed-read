package io.github.fianna76.speedread.data.backup

import io.github.fianna76.speedread.data.BookFileStore
import io.github.fianna76.speedread.data.db.BookCollectionEntity
import io.github.fianna76.speedread.data.db.BookDao
import io.github.fianna76.speedread.data.db.BookEntity
import io.github.fianna76.speedread.data.db.BookmarkDao
import io.github.fianna76.speedread.data.db.BookmarkEntity
import io.github.fianna76.speedread.data.db.ChapterEntity
import io.github.fianna76.speedread.data.db.CollectionDao
import io.github.fianna76.speedread.data.db.CollectionEntity
import io.github.fianna76.speedread.data.settings.UserPreferencesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** What a backup held, or what a restore added. */
data class BackupSummary(val books: Int, val bookmarks: Int, val collections: Int)

class BackupFormatException(message: String) : IOException(message)

/**
 * Backs up the library to one ZIP file: the book files and covers, reading positions,
 * bookmarks, collections and settings (news feeds included). Downloaded news articles are left
 * out: they are re-downloaded on demand.
 *
 * Restoring merges into the current library rather than replacing it. Books already present
 * (same content) keep whichever reading position is further along; bookmarks and collection
 * memberships are added; settings are replaced by the backup's.
 */
class LibraryBackup(
    private val books: BookDao,
    private val bookmarks: BookmarkDao,
    private val collections: CollectionDao,
    private val files: BookFileStore,
    private val preferences: UserPreferencesRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun export(output: OutputStream): BackupSummary = withContext(ioDispatcher) {
        val library = books.allBooks()
        val memberships = collections.allMemberships()
        val hashes = library.associate { it.id to it.contentHash }
        val backedUp = library.map { book ->
            BackupBook(
                title = book.title,
                author = book.author,
                language = book.language,
                format = book.format,
                fileName = book.fileName,
                coverFileName = book.coverFileName,
                contentHash = book.contentHash,
                totalWords = book.totalWords,
                addedAt = book.addedAt,
                lastOpenedAt = book.lastOpenedAt,
                chapterIndex = book.chapterIndex,
                wordIndex = book.wordIndex,
                wordsRead = book.wordsRead,
                chapters = books.chaptersOf(book.id).map { BackupChapter(it.index, it.title, it.href, it.wordCount, it.startWord) },
                bookmarks = bookmarks.of(book.id).map { BackupBookmark(it.chapterIndex, it.wordIndex, it.snippet, it.createdAt) },
            )
        }
        val manifest = BackupManifest(
            version = VERSION,
            exportedAt = clock(),
            books = backedUp,
            collections = collections.allCollections().map { collection ->
                BackupCollection(collection.name, collection.createdAt, memberships.filter { it.collectionId == collection.id }.mapNotNull { hashes[it.bookId] })
            },
            settings = preferences.exportJson(),
        )
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(JSON.encodeToString(manifest).toByteArray())
            zip.closeEntry()
            for (book in library) {
                addFile(zip, "$BOOKS/${book.fileName}", files.bookFile(book.fileName))
                book.coverFileName?.let { addFile(zip, "$COVERS/$it", files.coverFile(it)) }
            }
        }
        BackupSummary(backedUp.size, backedUp.sumOf { it.bookmarks.size }, manifest.collections.size)
    }

    /** @throws BackupFormatException if [input] isn't a SpeedRead backup. */
    suspend fun restore(input: InputStream): BackupSummary = withContext(ioDispatcher) {
        files.ensureDirectories()
        var manifest: BackupManifest? = null
        val staged = HashMap<String, File>() // Zip path -> temporary file.
        try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    when {
                        entry.isDirectory -> Unit
                        name == MANIFEST -> manifest = runCatching {
                            JSON.decodeFromString<BackupManifest>(zip.readLimited(MAX_MANIFEST_BYTES).decodeToString())
                        }.getOrElse { throw BackupFormatException("Unreadable manifest") }
                        SAFE_PATH.matches(name) -> staged[name] = files.createTempFile().also { temp ->
                            temp.outputStream().use { zip.copyTo(it) }
                        }
                        else -> Unit // Ignore anything unexpected, including path-traversal names.
                    }
                }
            }
            val backup = manifest ?: throw BackupFormatException("No manifest")
            if (backup.version > VERSION) throw BackupFormatException("Backup from a newer version")
            merge(backup, staged)
        } finally {
            staged.values.forEach { it.delete() }
        }
    }

    private suspend fun merge(backup: BackupManifest, staged: Map<String, File>): BackupSummary {
        var addedBooks = 0
        var addedBookmarks = 0
        val idsByHash = HashMap<String, Long>()
        for (book in backup.books) {
            if (!SAFE_NAME.matches(book.fileName) || book.coverFileName?.let(SAFE_NAME::matches) == false) continue
            val existing = books.findByHash(book.contentHash)
            val id = if (existing != null) {
                // Keep whichever copy is further along.
                if (book.wordsRead > existing.wordsRead) {
                    books.updatePosition(existing.id, book.chapterIndex, book.wordIndex, book.wordsRead)
                }
                if ((book.lastOpenedAt ?: 0) > (existing.lastOpenedAt ?: 0)) books.setLastOpened(existing.id, book.lastOpenedAt)
                existing.id
            } else {
                val file = staged["$BOOKS/${book.fileName}"] ?: continue // Book file missing from the backup.
                if (!file.renameTo(files.bookFile(book.fileName))) file.copyTo(files.bookFile(book.fileName), overwrite = true)
                book.coverFileName?.let { cover ->
                    staged["$COVERS/$cover"]?.let { it.copyTo(files.coverFile(cover), overwrite = true) }
                }
                addedBooks++
                books.insertBookWithChapters(
                    BookEntity(
                        title = book.title,
                        author = book.author,
                        language = book.language,
                        format = book.format,
                        fileName = book.fileName,
                        coverFileName = book.coverFileName?.takeIf { staged.containsKey("$COVERS/$it") },
                        contentHash = book.contentHash,
                        totalWords = book.totalWords,
                        addedAt = book.addedAt,
                        lastOpenedAt = book.lastOpenedAt,
                        chapterIndex = book.chapterIndex,
                        wordIndex = book.wordIndex,
                        wordsRead = book.wordsRead,
                    ),
                    book.chapters.map { ChapterEntity(0, it.index, it.title, it.href, it.wordCount, it.startWord) },
                )
            }
            idsByHash[book.contentHash] = id
            val marks = bookmarks.of(id).mapTo(HashSet()) { it.chapterIndex to it.wordIndex }
            for (mark in book.bookmarks) {
                if (marks.add(mark.chapterIndex to mark.wordIndex)) {
                    bookmarks.insert(BookmarkEntity(0, id, mark.chapterIndex, mark.wordIndex, mark.snippet, mark.createdAt))
                    addedBookmarks++
                }
            }
        }
        val current = collections.allCollections().associateBy { it.name }
        var addedCollections = 0
        for (collection in backup.collections) {
            val id = current[collection.name]?.id
                ?: collections.insertCollection(CollectionEntity(name = collection.name, createdAt = collection.createdAt)).also { addedCollections++ }
            collections.insertMemberships(collection.books.mapNotNull { idsByHash[it] }.map { BookCollectionEntity(id, it) })
        }
        backup.settings?.let { preferences.importJson(it) }
        return BackupSummary(addedBooks, addedBookmarks, addedCollections)
    }

    private fun addFile(zip: ZipOutputStream, path: String, file: File) {
        if (!file.isFile) return
        zip.putNextEntry(ZipEntry(path))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun ZipInputStream.readLimited(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size() > limit) throw BackupFormatException("Manifest too large")
        }
        return out.toByteArray()
    }

    @Serializable
    private data class BackupManifest(
        val version: Int,
        val exportedAt: Long,
        val books: List<BackupBook>,
        val collections: List<BackupCollection> = emptyList(),
        val settings: JsonObject? = null,
    )

    @Serializable
    private data class BackupBook(
        val title: String,
        val author: String?,
        val language: String?,
        val format: String,
        val fileName: String,
        val coverFileName: String?,
        val contentHash: String,
        val totalWords: Int,
        val addedAt: Long,
        val lastOpenedAt: Long?,
        val chapterIndex: Int,
        val wordIndex: Int,
        val wordsRead: Int,
        val chapters: List<BackupChapter>,
        val bookmarks: List<BackupBookmark> = emptyList(),
    )

    @Serializable
    private data class BackupChapter(val index: Int, val title: String?, val href: String, val wordCount: Int, val startWord: Int)

    @Serializable
    private data class BackupBookmark(val chapterIndex: Int, val wordIndex: Int, val snippet: String, val createdAt: Long)

    @Serializable
    private data class BackupCollection(val name: String, val createdAt: Long, val books: List<String>)

    companion object {
        private const val VERSION = 1
        private const val MANIFEST = "manifest.json"
        private const val BOOKS = "books"
        private const val COVERS = "covers"
        private const val MAX_MANIFEST_BYTES = 32 * 1024 * 1024
        /** Plain file names only: no separators, and no leading dot (so never "." or ".."). */
        private val SAFE_NAME = Regex("[A-Za-z0-9_-][A-Za-z0-9._-]{0,127}")
        private val SAFE_PATH = Regex("($BOOKS|$COVERS)/[A-Za-z0-9_-][A-Za-z0-9._-]{0,127}")
        private val JSON = Json { ignoreUnknownKeys = true }
    }
}

/** Backups to and from documents the user picks (Storage Access Framework). */
class BackupDocuments(private val resolver: android.content.ContentResolver, private val backup: LibraryBackup) {

    suspend fun export(uri: android.net.Uri): BackupSummary = withContext(Dispatchers.IO) {
        resolver.openOutputStream(uri, "wt")?.use { backup.export(it) } ?: throw IOException("Can't write $uri")
    }

    suspend fun restore(uri: android.net.Uri): BackupSummary = withContext(Dispatchers.IO) {
        resolver.openInputStream(uri)?.use { backup.restore(it) } ?: throw IOException("Can't read $uri")
    }
}
