package io.github.fianna76.speedread.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun findById(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE id IN (:ids)")
    suspend fun findByIds(ids: Collection<Long>): List<BookEntity>

    @Query("SELECT * FROM books WHERE content_hash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): BookEntity?

    @Query("SELECT * FROM chapters WHERE book_id = :bookId ORDER BY chapter_index")
    suspend fun chaptersOf(bookId: Long): List<ChapterEntity>

    @Insert
    suspend fun insertBook(book: BookEntity): Long

    @Insert
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    /** Inserts a book and its chapters atomically; [chapters] get the new book id. */
    @Transaction
    suspend fun insertBookWithChapters(book: BookEntity, chapters: List<ChapterEntity>): Long {
        val id = insertBook(book)
        insertChapters(chapters.map { it.copy(bookId = id) })
        return id
    }

    @Query(
        "UPDATE books SET chapter_index = :chapterIndex, word_index = :wordIndex, words_read = :wordsRead " +
            "WHERE id = :id",
    )
    suspend fun updatePosition(id: Long, chapterIndex: Int, wordIndex: Int, wordsRead: Int)

    @Query("UPDATE books SET last_opened_at = :timestamp WHERE id = :id")
    suspend fun updateLastOpened(id: Long, timestamp: Long)

    @Query("DELETE FROM chapters WHERE book_id IN (:ids)")
    suspend fun deleteChapters(ids: Collection<Long>)

    @Query("DELETE FROM books WHERE id IN (:ids)")
    suspend fun deleteBookRows(ids: Collection<Long>)

    /** Deletes books and their chapters (explicitly, not relying on foreign-key enforcement). */
    @Transaction
    suspend fun deleteBooks(ids: Collection<Long>) {
        deleteChapters(ids)
        deleteBookRows(ids)
    }
}
