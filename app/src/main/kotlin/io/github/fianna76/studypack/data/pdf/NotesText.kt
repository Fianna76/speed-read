package io.github.fianna76.studypack.data.pdf

import io.github.fianna76.speedread.rsvp.TokenizedText
import io.github.fianna76.speedread.rsvp.Tokenizer
import io.github.fianna76.studypack.data.files.NoteFileStore
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/** A notes PDF's text, tokenised for speed reading, with the word index where each page starts. */
class NotesText(val text: TokenizedText, private val pageStarts: IntArray) {

    val pageCount: Int get() = pageStarts.size

    /** Zero-based page holding word [index]. */
    fun pageOf(index: Int): Int {
        var page = 0
        for (i in pageStarts.indices) if (pageStarts[i] <= index) page = i else break
        return page
    }

    /** First word on [page], or on the next page with text. */
    fun startOf(page: Int): Int = pageStarts.getOrElse(page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))) { 0 }
        .coerceAtMost((text.size - 1).coerceAtLeast(0))

    companion object {
        fun from(pages: List<List<String>>): NotesText {
            val starts = IntArray(pages.size)
            var words = 0
            pages.forEachIndexed { i, paragraphs ->
                starts[i] = words
                words += Tokenizer.countWords(paragraphs)
            }
            return NotesText(Tokenizer.tokenize(pages.flatten()), starts)
        }
    }
}

/**
 * Extracts the text of notes PDFs for speed reading, caching it next to the app's other
 * regenerable files so a PDF is only parsed once.
 */
class NotesTextLoader(private val files: NoteFileStore, private val pdfs: PdfDocuments) {

    private val serializer = ListSerializer(ListSerializer(String.serializer()))

    /** Blocking; call off the main thread. Throws [PdfException] if the PDF can't be read. */
    fun load(internalPath: String): NotesText {
        val cache = files.textCacheFile(internalPath)
        val cached = readCache(cache)
        val pages = cached ?: pdfs.pageParagraphs(files.file(internalPath)).also { writeCache(cache, it) }
        return NotesText.from(pages)
    }

    private fun readCache(file: File): List<List<String>>? =
        if (!file.isFile) null else runCatching { Json.decodeFromString(serializer, file.readText()) }.getOrNull()

    private fun writeCache(file: File, pages: List<List<String>>) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(Json.encodeToString(serializer, pages))
        }
    }
}
