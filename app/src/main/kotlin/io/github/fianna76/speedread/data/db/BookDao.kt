package io.github.fianna76.speedread.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class ArticleProgressRow(
    @ColumnInfo(name = "source_url") val sourceUrl: String,
    @ColumnInfo(name = "words_read") val wordsRead: Int,
    @ColumnInfo(name = "total_words") val totalWords: Int,
)

@Dao
interface BookDao {

    /** The library: imported books, without saved news articles. */
    @Query("SELECT * FROM books WHERE format != 'ARTICLE'")
    fun observeBooks(): Flow<List<BookEntity>>

    /** Reading progress of saved news articles. */
    @Query("SELECT source_url, words_read, total_words FROM books WHERE format = 'ARTICLE' AND source_url IS NOT NULL")
    fun observeArticleProgress(): Flow<List<ArticleProgressRow>>

    @Query("SELECT * FROM books WHERE format = 'ARTICLE' AND source_url = :url LIMIT 1")
    suspend fun findArticle(url: String): BookEntity?

    /** Downloaded articles not opened since [cutoff] (epoch millis), except those on the reading list. */
    @Query(
        "SELECT * FROM books WHERE format = 'ARTICLE' AND COALESCE(last_opened_at, added_at) < :cutoff " +
            "AND source_url NOT IN (SELECT url FROM news_history WHERE saved_at IS NOT NULL)",
    )
    suspend fun findArticlesOlderThan(cutoff: Long): List<BookEntity>

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
