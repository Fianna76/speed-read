package io.github.fianna76.speedread.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** An imported book plus its reading position (one row per book). */
@Entity(
    tableName = "books",
    indices = [Index(value = ["content_hash"], unique = true)],
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String?,
    val language: String?,
    /** [io.github.fianna76.speedread.data.BookFormat] name; added in schema version 2. */
    @ColumnInfo(name = "format", defaultValue = "EPUB") val format: String = "EPUB",
    /** File name of the book inside the app's private books directory. */
    @ColumnInfo(name = "file_name") val fileName: String,
    /** File name of the down-scaled cover inside the covers directory, if the book has one. */
    @ColumnInfo(name = "cover_file_name") val coverFileName: String?,
    /** SHA-256 of the book file, used to detect duplicate imports. */
    @ColumnInfo(name = "content_hash") val contentHash: String,
    @ColumnInfo(name = "total_words") val totalWords: Int,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "last_opened_at") val lastOpenedAt: Long? = null,
    @ColumnInfo(name = "chapter_index") val chapterIndex: Int = 0,
    @ColumnInfo(name = "word_index") val wordIndex: Int = 0,
    /** Absolute position in the book in words; drives the progress shown in the library. */
    @ColumnInfo(name = "words_read") val wordsRead: Int = 0,
)

/** A readable section of a book (one spine document with text), in reading order. */
@Entity(
    tableName = "chapters",
    primaryKeys = ["book_id", "chapter_index"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ChapterEntity(
    @ColumnInfo(name = "book_id") val bookId: Long,
    @ColumnInfo(name = "chapter_index") val index: Int,
    /** Title from the table of contents, first heading or PDF outline; `null` when none exists. */
    val title: String?,
    /** EPUB: path of the content document in the archive. PDF: page range, see `PdfPageRange`. */
    val href: String,
    @ColumnInfo(name = "word_count") val wordCount: Int,
    /** Number of words in all preceding chapters. */
    @ColumnInfo(name = "start_word") val startWord: Int,
)
