package io.github.fianna76.speedread.ui.reader

import io.github.fianna76.speedread.data.Bookmark

/** Turns a book's highlights, bookmarks and notes into Markdown, grouped by chapter in reading order. */
object NotesExport {

    fun markdown(title: String, bookmarks: List<Bookmark>, chapterTitle: (Int) -> String): String = buildString {
        append("# ").append(title).append("\n")
        bookmarks
            .sortedWith(compareBy({ it.position.chapterIndex }, { it.position.wordIndex }))
            .groupBy { it.position.chapterIndex }
            .forEach { (chapter, marks) ->
                append("\n## ").append(chapterTitle(chapter)).append("\n")
                for (mark in marks) {
                    append("\n")
                    if (mark.highlight != null) {
                        append("> ").append(mark.highlight).append("\n")
                    } else {
                        append("- Bookmark: ").append(mark.snippet).append("…\n")
                    }
                    mark.note?.let { append("\n").append(it).append("\n") }
                }
            }
    }
}
