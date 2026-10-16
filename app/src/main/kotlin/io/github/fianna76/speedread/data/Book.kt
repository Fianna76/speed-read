package io.github.fianna76.speedread.data

/** A book in the library, as presented to the UI. */
data class Book(
    val id: Long,
    val title: String,
    val author: String?,
    /** Absolute path of the cover thumbnail, or `null` to show a generated placeholder. */
    val coverPath: String?,
    val totalWords: Int,
    val addedAt: Long,
    val lastOpenedAt: Long?,
    val position: ReadingPosition,
    /** Words before the reading position, across all chapters. */
    val wordsRead: Int,
) {
    /** Reading progress in `[0, 1]`. */
    val progress: Float
        get() = if (totalWords <= 0) 0f else (wordsRead.toFloat() / totalWords).coerceIn(0f, 1f)
}

/** Where the reader left off: a chapter and a word within it. */
data class ReadingPosition(val chapterIndex: Int, val wordIndex: Int)

/** A readable section of a book. */
data class Chapter(
    val index: Int,
    /** Title from the table of contents or the first heading, if any. */
    val title: String?,
    val href: String,
    val wordCount: Int,
    /** Words in all preceding chapters. */
    val startWord: Int,
)
