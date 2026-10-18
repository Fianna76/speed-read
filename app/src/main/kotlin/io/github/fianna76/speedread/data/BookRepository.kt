package io.github.fianna76.speedread.data

import io.github.fianna76.speedread.data.db.BookDao
import io.github.fianna76.speedread.data.db.BookEntity
import io.github.fianna76.speedread.data.db.BookmarkDao
import io.github.fianna76.speedread.data.db.BookmarkEntity
import io.github.fianna76.speedread.data.db.ChapterEntity
import io.github.fianna76.speedread.data.pdf.PdfBooks
import io.github.fianna76.speedread.data.pdf.PdfPageRange
import io.github.fianna76.speedread.epub.EpubArchive
import io.github.fianna76.speedread.epub.XhtmlTextExtractor
import io.github.fianna76.speedread.rsvp.TokenizedText
import io.github.fianna76.speedread.rsvp.Tokenizer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException

/** Access to the library and the text of its books. */
interface BookRepository {

    fun observeBooks(): Flow<List<Book>>

    suspend fun getBook(id: Long): Book?

    suspend fun getChapters(bookId: Long): List<Chapter>

    /** Extracts and tokenises the text of [chapter]. */
    suspend fun loadChapterText(bookId: Long, chapter: Chapter): TokenizedText

    suspend fun savePosition(bookId: Long, position: ReadingPosition, wordsRead: Int)

    suspend fun markOpened(bookId: Long)

    suspend fun deleteBooks(ids: Set<Long>)

    /** The book or article read most recently and not finished, for "Continue reading". */
    suspend fun lastUnfinishedBookId(): Long? = null

    /** [bookId]'s bookmarks in reading order. */
    fun observeBookmarks(bookId: Long): Flow<List<Bookmark>> = flowOf(emptyList())

    suspend fun addBookmark(bookId: Long, position: ReadingPosition, snippet: String) {}

    suspend fun deleteBookmark(id: Long) {}
}

/** [BookRepository] backed by Room and the app-private [BookFileStore]. */
class OfflineBookRepository(
    private val dao: BookDao,
    private val files: BookFileStore,
    private val pdfBooks: PdfBooks,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val bookmarks: BookmarkDao? = null,
) : BookRepository {

    override fun observeBooks(): Flow<List<Book>> =
        dao.observeBooks().map { entities -> entities.map { it.toBook() } }

    override suspend fun getBook(id: Long): Book? = dao.findById(id)?.toBook()

    override suspend fun getChapters(bookId: Long): List<Chapter> = dao.chaptersOf(bookId).map { it.toChapter() }

    override suspend fun loadChapterText(bookId: Long, chapter: Chapter): TokenizedText = withContext(ioDispatcher) {
        val entity = dao.findById(bookId) ?: throw NoSuchElementException("Book $bookId not found")
        val file = files.bookFile(entity.fileName)
        val paragraphs = when (BookFormat.fromStored(entity.format)) {
            BookFormat.EPUB -> EpubArchive.open(file).use { archive ->
                XhtmlTextExtractor.extract(archive.readBytes(chapter.href)).paragraphs
            }
            BookFormat.ARTICLE -> files.readChapterText(entity.fileName, chapter.href)
                ?: throw IOException("Text of article $bookId is missing")
            BookFormat.PDF -> files.readChapterText(entity.fileName, chapter.href) ?: run {
                val pages = PdfPageRange.fromHref(chapter.href) ?: throw IOException("Bad page range ${chapter.href}")
                pdfBooks.open(file).use { it.paragraphs(pages) }
                    .also { files.writeChapterText(entity.fileName, chapter.href, it) }
            }
        }
        Tokenizer.tokenize(paragraphs)
    }

    override suspend fun savePosition(bookId: Long, position: ReadingPosition, wordsRead: Int) {
        dao.updatePosition(bookId, position.chapterIndex, position.wordIndex, wordsRead)
    }

    override suspend fun markOpened(bookId: Long) {
        dao.updateLastOpened(bookId, clock())
    }

    override suspend fun lastUnfinishedBookId(): Long? = dao.findLastUnfinished()?.id

    override fun observeBookmarks(bookId: Long): Flow<List<Bookmark>> =
        bookmarks?.observe(bookId)?.map { rows ->
            rows.map { Bookmark(it.id, ReadingPosition(it.chapterIndex, it.wordIndex), it.snippet, it.createdAt) }
        } ?: flowOf(emptyList())

    override suspend fun addBookmark(bookId: Long, position: ReadingPosition, snippet: String) {
        bookmarks?.insert(BookmarkEntity(0, bookId, position.chapterIndex, position.wordIndex, snippet, clock()))
    }

    override suspend fun deleteBookmark(id: Long) {
        bookmarks?.delete(id)
    }

    override suspend fun deleteBooks(ids: Set<Long>) {
        if (ids.isEmpty()) return
        val entities = dao.findByIds(ids)
        dao.deleteBooks(ids)
        withContext(ioDispatcher) {
            entities.forEach { files.delete(it.fileName, it.coverFileName) }
        }
    }

    private fun BookEntity.toBook() = Book(
        id = id,
        title = title,
        author = author,
        coverPath = coverFileName?.let { files.coverFile(it).absolutePath },
        totalWords = totalWords,
        addedAt = addedAt,
        lastOpenedAt = lastOpenedAt,
        position = ReadingPosition(chapterIndex, wordIndex),
        wordsRead = wordsRead,
        format = BookFormat.fromStored(format),
        sourceUrl = sourceUrl,
    )

    private fun ChapterEntity.toChapter() = Chapter(
        index = index,
        title = title,
        href = href,
        wordCount = wordCount,
        startWord = startWord,
    )
}
