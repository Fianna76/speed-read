package io.github.fianna76.speedread.data.pdf

/**
 * Turns raw text extracted from PDF pages into readable paragraphs. PDFs store positioned lines,
 * not paragraphs, so extracted text is full of artefacts that would disrupt RSVP:
 *
 * - lines wrapped inside paragraphs are joined, undoing end-of-line hyphenation
 *   (`compre-` + `hension` → `comprehension`) when the next line continues in lowercase;
 * - lines holding only a page number ("12", "Page 3", "4 of 20") are dropped;
 * - soft hyphens and runs of whitespace are cleaned up.
 */
internal object PdfTextCleaner {

    /** Separator the extractor writes between paragraphs it detects. */
    const val PARAGRAPH_MARK = '\u2029'

    private val BLANK_LINE = Regex("\\n\\s*\\n")
    private val WHITESPACE_RUN = Regex("[\\s\\p{Z}\\u200B]+")
    private val PAGE_NUMBER = Regex("^(page\\s*)?\\d{1,4}(\\s*(of|/)\\s*\\d{1,4})?$", RegexOption.IGNORE_CASE)

    fun paragraphs(raw: String): List<String> =
        raw.split(PARAGRAPH_MARK)
            .flatMap { it.split(BLANK_LINE) }
            .mapNotNull { block ->
                val lines = block.lines()
                    .map { it.replace("\u00AD", "").trim() }
                    .filter { it.isNotEmpty() && !PAGE_NUMBER.matches(it) }
                if (lines.isEmpty()) null else joinLines(lines).replace(WHITESPACE_RUN, " ").trim().ifEmpty { null }
            }

    private fun joinLines(lines: List<String>): String {
        val text = StringBuilder(lines.first())
        for (line in lines.drop(1)) {
            val hyphenated = text.length >= 2 && text.last() == '-' && text[text.length - 2].isLetter()
            if (hyphenated && line.first().isLowerCase()) {
                text.setLength(text.length - 1)
                text.append(line)
            } else {
                text.append(' ').append(line)
            }
        }
        return text.toString()
    }
}
