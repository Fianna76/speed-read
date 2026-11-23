package io.github.fianna76.studypack.data.pdf

import io.github.fianna76.speedread.rsvp.TokenizedText
import io.github.fianna76.speedread.rsvp.Tokenizer
import io.github.fianna76.studypack.data.files.NoteFileStore
import kotlinx.serialization.json.Json
import java.io.File

/** One section of a notes PDF, tokenised for speed reading. */
class NotesChapter(
    /** The section's heading, or `null` for front matter and page blocks. */
    val title: String?,
    val kind: SectionKind,
    val text: TokenizedText,
    val firstPage: Int,
    /** For each page from [firstPage], the index of its first word in [text]. */
    private val pageStarts: IntArray,
) {
    val lastPage: Int get() = firstPage + pageStarts.size - 1

    val wordCount: Int get() = text.size

    /** Zero-based PDF page of word [index]. */
    fun pageOf(index: Int): Int {
        var page = 0
        for (i in pageStarts.indices) if (pageStarts[i] <= index) page = i else break
        return firstPage + page
    }

    /** First word on [page], if the chapter has text there or later. */
    fun startOf(page: Int): Int? {
        val i = page - firstPage
        if (i !in pageStarts.indices) return null
        return pageStarts[i].takeIf { it < text.size }
    }
}

/** A reading position: word [index] of chapter [chapter]. */
data class NotesPosition(val chapter: Int, val index: Int)

/**
 * A notes PDF as chapters for speed reading. Front matter (title page, contents) and back matter
 * (sources) stay available as chapters, but reading starts after the front matter and does not
 * run on into the back matter (see [nextChapterAfter]).
 */
class NotesBook(val chapters: List<NotesChapter>) {

    val totalWords: Int = chapters.sumOf { it.wordCount }

    /** Word offset of each chapter in the whole document. */
    private val chapterStarts: IntArray = chapters.runningFold(0) { acc, c -> acc + c.wordCount }.dropLast(1).toIntArray()

    fun startWordOf(chapter: Int): Int = chapterStarts.getOrElse(chapter) { totalWords }

    /** The first chapter that is part of the notes themselves (not front or back matter). */
    val firstBodyChapter: Int = chapters.indexOfFirst { it.kind == SectionKind.BODY }.takeIf { it >= 0 } ?: 0

    /** Where reading starts by default: the first section, after the title page and contents. */
    val start: NotesPosition get() = NotesPosition(firstBodyChapter, 0)

    /** The position of absolute word [word], or `null` if out of range. */
    fun locate(word: Int): NotesPosition? {
        if (word !in 0 until totalWords) return null
        val chapter = chapterStarts.indexOfLast { it <= word }
        return NotesPosition(chapter, word - chapterStarts[chapter])
    }

    fun absolute(position: NotesPosition): Int = startWordOf(position.chapter) + position.index

    /**
     * Where to start reading for PDF page [page]: the body chapter holding it, at that page's first
     * word. A page in the front matter starts at the first section instead.
     */
    fun positionForPage(page: Int): NotesPosition {
        chapters.forEachIndexed { i, chapter ->
            if (chapter.kind == SectionKind.FRONT) return@forEachIndexed
            val start = chapter.startOf(page)
            if (start != null && page <= chapter.lastPage) return NotesPosition(i, start)
        }
        // Past the end, or only front matter: fall back to the default start.
        val last = chapters.indexOfLast { it.kind != SectionKind.FRONT && page >= it.firstPage }
        return if (last >= 0) NotesPosition(last, 0) else start
    }

    /**
     * The chapter reading continues with when [chapter] ends: the next section of the notes.
     * Front and back matter are skipped; a chapter of back matter the reader chose explicitly
     * continues with the next one. `null` at the end.
     */
    fun nextChapterAfter(chapter: Int): Int? {
        val current = chapters.getOrNull(chapter) ?: return null
        val next = (chapter + 1 until chapters.size)
        return if (current.kind == SectionKind.BACK) {
            next.firstOrNull()
        } else {
            next.firstOrNull { chapters[it].kind == SectionKind.BODY }
        }
    }

    companion object {

        fun from(extracted: ExtractedPdf): NotesBook {
            val pages = extracted.pages
            val sections = NotesStructure.detect(pages, extracted.outline)
            val chapters = sections.mapIndexedNotNull { i, section ->
                val end = sections.getOrNull(i + 1)
                chapter(pages, section, endPage = end?.startPage ?: pages.size, endParagraph = end?.startParagraph ?: 0)
            }
            return NotesBook(chapters)
        }

        /** The paragraphs from [section]'s start up to (not including) [endPage]/[endParagraph]. */
        private fun chapter(pages: List<PageText>, section: NotesSection, endPage: Int, endParagraph: Int): NotesChapter? {
            val paragraphs = ArrayList<String>()
            val pageStarts = ArrayList<Int>()
            var words = 0
            val lastPage = if (endParagraph == 0) endPage - 1 else endPage
            for (page in section.startPage..lastPage.coerceAtMost(pages.lastIndex)) {
                val all = pages[page].paragraphs
                val from = if (page == section.startPage) section.startParagraph else 0
                val to = if (page == endPage) endParagraph else all.size
                pageStarts += words
                for (paragraph in all.subList(from.coerceAtMost(all.size), to.coerceIn(from.coerceAtMost(all.size), all.size))) {
                    paragraphs += paragraph
                    words += Tokenizer.countWords(listOf(paragraph))
                }
            }
            if (words == 0) return null
            return NotesChapter(section.title, section.kind, Tokenizer.tokenize(paragraphs), section.startPage, pageStarts.toIntArray())
        }
    }
}

/**
 * Extracts the text of notes PDFs for speed reading, caching it next to the app's other
 * regenerable files so a PDF is only parsed once.
 */
class NotesTextLoader(private val files: NoteFileStore, private val pdfs: PdfDocuments) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Blocking; call off the main thread. Throws [PdfException] if the PDF can't be read. */
    fun load(internalPath: String): NotesBook {
        val cache = files.textCacheFile(internalPath)
        val extracted = readCache(cache) ?: pdfs.extract(files.file(internalPath)).also { writeCache(cache, it) }
        return NotesBook.from(extracted)
    }

    private fun readCache(file: File): ExtractedPdf? =
        if (!file.isFile) null else runCatching { json.decodeFromString(ExtractedPdf.serializer(), file.readText()) }.getOrNull()

    private fun writeCache(file: File, extracted: ExtractedPdf) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(ExtractedPdf.serializer(), extracted))
        }
    }
}
