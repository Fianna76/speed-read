package io.github.fianna76.speedread.testing

import io.github.fianna76.speedread.data.Book
import io.github.fianna76.speedread.data.BookCollection
import io.github.fianna76.speedread.data.BookRepository
import io.github.fianna76.speedread.data.Bookmark
import io.github.fianna76.speedread.data.Chapter
import io.github.fianna76.speedread.data.ReadingPosition
import io.github.fianna76.speedread.rsvp.TokenizedText
import io.github.fianna76.speedread.rsvp.Tokenizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.io.IOException

/** In-memory [BookRepository]; chapter text is given as paragraphs keyed by (bookId, chapter index). */
class FakeBookRepository(
    books: List<Book> = emptyList(),
    private val chapters: Map<Long, List<Chapter>> = emptyMap(),
    private val texts: Map<Pair<Long, Int>, List<String>> = emptyMap(),
) : BookRepository {

    val books = MutableStateFlow(books)
    val savedPositions = mutableListOf<SavedPosition>()
    val openedBooks = mutableListOf<Long>()
    var failChapterLoads = false

    data class SavedPosition(val bookId: Long, val position: ReadingPosition, val wordsRead: Int)

    override fun observeBooks(): Flow<List<Book>> = books

    override suspend fun getBook(id: Long): Book? = books.value.firstOrNull { it.id == id }

    override suspend fun getChapters(bookId: Long): List<Chapter> = chapters[bookId].orEmpty()

    override suspend fun loadChapterText(bookId: Long, chapter: Chapter): TokenizedText {
        if (failChapterLoads) throw IOException("Simulated read failure")
        return Tokenizer.tokenize(texts[bookId to chapter.index].orEmpty())
    }

    override suspend fun savePosition(bookId: Long, position: ReadingPosition, wordsRead: Int) {
        savedPositions += SavedPosition(bookId, position, wordsRead)
        books.update { list ->
            list.map { if (it.id == bookId) it.copy(position = position, wordsRead = wordsRead) else it }
        }
    }

    override suspend fun markOpened(bookId: Long) {
        openedBooks += bookId
    }

    val bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    private var nextBookmarkId = 1L

    override fun observeBookmarks(bookId: Long): Flow<List<Bookmark>> =
        bookmarks.map { list -> list.sortedWith(compareBy({ it.position.chapterIndex }, { it.position.wordIndex })) }

    override suspend fun addBookmark(bookId: Long, position: ReadingPosition, snippet: String, highlight: String?) {
        bookmarks.update { it + Bookmark(nextBookmarkId++, position, snippet, createdAt = 0, highlight = highlight) }
    }

    override suspend fun setBookmarkNote(id: Long, note: String?) {
        bookmarks.update { list -> list.map { if (it.id == id) it.copy(note = note?.trim()?.takeIf(String::isNotEmpty)) else it } }
    }

    override suspend fun deleteBookmark(id: Long) {
        bookmarks.update { list -> list.filterNot { it.id == id } }
    }

    val collections = MutableStateFlow<List<BookCollection>>(emptyList())
    private var nextCollectionId = 1L

    override fun observeCollections(): Flow<List<BookCollection>> = collections

    override suspend fun createCollection(name: String, bookIds: Set<Long>): Long {
        val id = nextCollectionId++
        collections.update { it + BookCollection(id, name.trim(), bookIds) }
        return id
    }

    override suspend fun addToCollection(collectionId: Long, bookIds: Set<Long>) = updateCollection(collectionId) { it.copy(bookIds = it.bookIds + bookIds) }

    override suspend fun removeFromCollection(collectionId: Long, bookIds: Set<Long>) =
        updateCollection(collectionId) { it.copy(bookIds = it.bookIds - bookIds) }

    override suspend fun renameCollection(collectionId: Long, name: String) = updateCollection(collectionId) { it.copy(name = name.trim()) }

    override suspend fun deleteCollection(collectionId: Long) {
        collections.update { list -> list.filterNot { it.id == collectionId } }
    }

    private fun updateCollection(id: Long, change: (BookCollection) -> BookCollection) {
        collections.update { list -> list.map { if (it.id == id) change(it) else it } }
    }

    override suspend fun deleteBooks(ids: Set<Long>) {
        books.update { list -> list.filterNot { it.id in ids } }
    }
}

fun testBook(
    id: Long,
    title: String = "Book $id",
    author: String? = "Author $id",
    totalWords: Int = 100,
    addedAt: Long = 0,
    lastOpenedAt: Long? = null,
    position: ReadingPosition = ReadingPosition(0, 0),
    wordsRead: Int = 0,
) = Book(id, title, author, coverPath = null, totalWords, addedAt, lastOpenedAt, position, wordsRead)
