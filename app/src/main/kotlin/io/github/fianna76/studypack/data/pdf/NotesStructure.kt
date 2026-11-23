package io.github.fianna76.studypack.data.pdf

/** What a section of a notes PDF is, for deciding what speed reading skips. */
enum class SectionKind {
    /** Title page, contents and anything before the first section: skipped. */
    FRONT,

    /** The notes themselves. */
    BODY,

    /** Sources, references and the like after the last section: skipped. */
    BACK,
}

/**
 * Where a section starts: page [startPage], paragraph [startParagraph] of that page's
 * [PageText.paragraphs]. [title] is `null` for front matter and for page blocks.
 */
data class NotesSection(
    val title: String?,
    val kind: SectionKind,
    val startPage: Int,
    val startParagraph: Int,
)

/**
 * Finds the sections of a notes PDF, so speed reading can offer them as chapters and skip the
 * front matter.
 *
 * Overview notes from the study-pack builder share one layout: a title page, a "Contents" page,
 * numbered sections ("1. The big picture", "2. Requirements", …, "Formula sheet") and a closing
 * "Sources" list. The detector reads it in that order of trust:
 *
 * 1. the **contents page** (a page in the first five whose line reads "Contents"): its entries are
 *    matched, in order, to the heading lines that start each section in the body;
 * 2. otherwise the PDF's **bookmarks**;
 * 3. otherwise **numbered headings** ("1. …", "2. …", each the next number, on a line of its own).
 *
 * Everything before the first section (title page, contents, a preface) is [SectionKind.FRONT];
 * "Sources", "References" and similar after the body are [SectionKind.BACK]. A PDF with no
 * recognisable structure is split into blocks of [PAGES_PER_BLOCK] pages, all [SectionKind.BODY].
 */
object NotesStructure {

    const val PAGES_PER_BLOCK = 5

    /** How far into the document a contents page may be. */
    private const val CONTENTS_SEARCH_PAGES = 5

    /** A first page with fewer words than this, in a longer document, is taken as a title page. */
    private const val TITLE_PAGE_MAX_WORDS = 80

    private const val MAX_HEADING_WORDS = 12

    private val PAGE_NUMBER = Regex("""^(page\s*)?\d{1,4}(\s*(of|/)\s*\d{1,4})?$""", RegexOption.IGNORE_CASE)

    private val CONTENTS_HEADING =Regex("""^(table of\s+)?contents$""", RegexOption.IGNORE_CASE)

    /** "3. Stage 1 (A1, A2) ........ 7", "3 Stage 1 7", "Sources 19"; group 1 = number, 2 = title. */
    private val ENTRY_WITH_PAGE = Regex("""^(?:(\d{1,2}(?:\.\d{1,2})*)\.?\s+)?(\p{L}.*?)[\s.·…_]*\s(\d{1,3})$""")

    /** "3. Stage 1 (A1, A2)" with no page number (renderers that can't print them). */
    private val ENTRY_NUMBERED = Regex("""^(\d{1,2}(?:\.\d{1,2})*)\.?\s+(\p{L}.*)$""")

    /** A numbered heading on a line of its own: "4. Corners, phase & poles". */
    private val NUMBERED_HEADING = Regex("""^(\d{1,2})\.?\s+(\p{Lu}.*)$""")

    private val BACK_TITLES = Regex(
        """^(sources|references|bibliography|further reading|works cited|acknowledg(e)?ments?|credits)\b""",
        RegexOption.IGNORE_CASE,
    )

    private data class Entry(val number: String?, val title: String) {
        val isTopLevel: Boolean get() = number == null || '.' !in number
    }

    private data class Heading(val title: String, val page: Int, val paragraph: Int)

    /** A position in the document, ordered by page then paragraph. */
    private data class Cursor(val page: Int, val paragraph: Int) : Comparable<Cursor> {
        override fun compareTo(other: Cursor) = compareValuesBy(this, other, { it.page }, { it.paragraph })
    }

    fun detect(rawPages: List<PageText>, outline: List<OutlineMark> = emptyList()): List<NotesSection> {
        if (rawPages.isEmpty()) return emptyList()
        val pages = withoutRunningLines(rawPages)
        val contents = findContents(pages)
        val titlePage = contents?.first != 0 && looksLikeTitlePage(pages)
        val bodyStart = when {
            contents != null -> contents.last + 1
            titlePage -> 1
            else -> 0
        }.coerceAtMost(pages.size)

        var headings = contents?.let { matchEntries(contentsEntries(pages, it), pages, bodyStart) }.orEmpty()
        if (headings.size < 2) headings = outlineHeadings(outline, pages, bodyStart)
        if (headings.size < 2) headings = numberedHeadings(pages, bodyStart)

        val hasFrontMatter = contents != null || titlePage
        if (headings.isEmpty()) return pageBlocks(pages, if (hasFrontMatter) bodyStart else 0)

        val sections = ArrayList<NotesSection>()
        val first = headings.first()
        if (first.page > 0 || first.paragraph > 0) {
            // Text before the first section: front matter if there is a title page or contents,
            // otherwise real content that just has no heading of its own.
            sections += NotesSection(null, if (hasFrontMatter) SectionKind.FRONT else SectionKind.BODY, 0, 0)
        }
        headings.forEachIndexed { i, heading ->
            val kind = if (i > 0 && BACK_TITLES.containsMatchIn(stripNumber(heading.title))) SectionKind.BACK else SectionKind.BODY
            sections += NotesSection(heading.title, kind, heading.page, heading.paragraph)
        }
        // Once back matter starts, everything after it is back matter too.
        val firstBack = sections.indexOfFirst { it.kind == SectionKind.BACK }
        return if (firstBack < 0) sections else sections.mapIndexed { i, s -> if (i > firstBack) s.copy(kind = SectionKind.BACK) else s }
    }

    /**
     * The pages with running headers and footers removed (a line repeated on at least half the
     * pages, such as "EE5105 · ECG amp"), along with bare page numbers, so they are never taken
     * for contents entries or headings. Paragraphs are left as they are.
     */
    private fun withoutRunningLines(pages: List<PageText>): List<PageText> {
        val running = if (pages.size < 3) {
            emptySet()
        } else {
            pages.flatMap { page -> page.lines.map { normalize(it) }.toSet() }
                .groupingBy { it }.eachCount()
                .filter { (line, count) -> line.isNotEmpty() && count * 2 >= pages.size }
                .keys
        }
        return pages.map { page ->
            page.copy(lines = page.lines.filter { line -> !PAGE_NUMBER.matches(line.trim()) && normalize(line) !in running })
        }
    }

    /** Pages holding the contents list, or `null` if there is none. */
    private fun findContents(pages: List<PageText>): IntRange? {
        val start = (0 until minOf(CONTENTS_SEARCH_PAGES, pages.size)).firstOrNull { i ->
            pages[i].lines.any { CONTENTS_HEADING.matches(it.trim()) }
        } ?: return null
        var end = start
        // The list can run onto further pages: pages made mostly of "title ... page" lines.
        while (end + 1 < pages.size) {
            val lines = pages[end + 1].lines
            val entries = lines.count { ENTRY_WITH_PAGE.matches(it) }
            if (entries >= 3 && entries * 10 >= lines.size * 6) end++ else break
        }
        return start..end
    }

    private fun contentsEntries(pages: List<PageText>, range: IntRange): List<Entry> {
        val entries = ArrayList<Entry>()
        for (page in range) {
            val lines = pages[page].lines
            val from = if (page == range.first) lines.indexOfFirst { CONTENTS_HEADING.matches(it.trim()) } + 1 else 0
            for (line in lines.drop(from)) {
                val text = line.trim()
                if (text.all { it.isDigit() || it.isWhitespace() }) continue // a page number on its own line
                val entry = ENTRY_WITH_PAGE.matchEntire(text)?.let { Entry(it.groupValues[1].ifEmpty { null }, it.groupValues[2].trim()) }
                    ?: ENTRY_NUMBERED.matchEntire(text)?.let { Entry(it.groupValues[1], it.groupValues[2].trim()) }
                    ?: text.takeIf { it.split(' ').size <= 6 && it.first().isLetter() }?.let { Entry(null, it) }
                    ?: continue
                if (entry.isTopLevel) entries += entry
            }
        }
        return entries
    }

    /** Finds each contents entry's heading line in the body, in order. Entries not found are skipped. */
    private fun matchEntries(entries: List<Entry>, pages: List<PageText>, bodyStart: Int): List<Heading> {
        val headings = ArrayList<Heading>()
        var cursor = Cursor(bodyStart, 0)
        for (entry in entries) {
            val title = normalize(entry.title)
            if (title.isEmpty()) continue
            val found = findHeadingLine(pages, cursor) { line ->
                val text = normalize(line)
                val withoutNumber = normalize(stripNumber(line))
                when {
                    entry.number != null -> text == normalize("${entry.number} ${entry.title}") ||
                        // A long heading that wraps: its first line is a good prefix of the title.
                        (text.startsWith(normalize(entry.number) + " ") && withoutNumber.length >= 12 && title.startsWith(withoutNumber))
                    else -> withoutNumber == title
                }
            } ?: continue
            headings += Heading(displayTitle(entry), found.page, found.paragraph)
            cursor = Cursor(found.page, found.paragraph + 1)
        }
        return headings
    }

    private fun outlineHeadings(outline: List<OutlineMark>, pages: List<PageText>, bodyStart: Int): List<Heading> {
        val headings = ArrayList<Heading>()
        var cursor = Cursor(bodyStart, 0)
        for (mark in outline) {
            if (mark.page !in pages.indices || mark.page < cursor.page) continue
            val title = normalize(stripNumber(mark.title))
            val paragraphs = pages[mark.page].paragraphs
            val from = if (mark.page == cursor.page) cursor.paragraph else 0
            if (paragraphs.isNotEmpty() && from >= paragraphs.size) continue
            // A bookmark points at a page; start at its heading's paragraph when it can be found.
            val paragraph = (from until paragraphs.size).firstOrNull { normalize(stripNumber(paragraphs[it])).startsWith(title) } ?: from
            headings += Heading(mark.title.trim(), mark.page, paragraph)
            cursor = Cursor(mark.page, paragraph + 1)
        }
        return headings
    }

    /** "1. …", "2. …": each must be the next number, on its own short line, not ending like a sentence. */
    private fun numberedHeadings(pages: List<PageText>, bodyStart: Int): List<Heading> {
        val headings = ArrayList<Heading>()
        var expected: Int? = null
        var cursor = Cursor(bodyStart, 0)
        while (true) {
            val wanted = expected
            val found = findHeadingLine(pages, cursor) { line ->
                val match = NUMBERED_HEADING.matchEntire(line) ?: return@findHeadingLine false
                val number = match.groupValues[1].toInt()
                val words = line.split(' ').size
                (wanted == null && number in 0..1 || number == wanted) && words <= MAX_HEADING_WORDS && line.last() !in ".,;:"
            } ?: break
            val line = found.line
            headings += Heading(line, found.page, found.paragraph)
            expected = NUMBERED_HEADING.matchEntire(line)!!.groupValues[1].toInt() + 1
            cursor = Cursor(found.page, found.paragraph + 1)
        }
        if (headings.isNotEmpty()) {
            // An unnumbered "Sources" after the last numbered section.
            findHeadingLine(pages, cursor) { line -> BACK_TITLES.matches(line.trim()) || BACK_TITLES.containsMatchIn(line) && line.split(' ').size <= 3 }
                ?.let { headings += Heading(it.line, it.page, it.paragraph) }
        }
        return headings
    }

    private class Found(val page: Int, val paragraph: Int, val line: String)

    /**
     * The first line at or after [from] that [matches], with the paragraph it starts. Headings
     * are usually a paragraph of their own; when the text extractor merged one into the next
     * paragraph, that paragraph starts with it.
     */
    private fun findHeadingLine(pages: List<PageText>, from: Cursor, matches: (String) -> Boolean): Found? {
        for (page in from.page until pages.size) {
            val text = pages[page]
            val minParagraph = if (page == from.page) from.paragraph else 0
            for (line in text.lines) {
                if (!matches(line.trim())) continue
                val key = normalize(line)
                val paragraph = text.paragraphs.indices.firstOrNull { i -> i >= minParagraph && normalize(text.paragraphs[i]).startsWith(key) }
                    ?: text.paragraphs.indices.firstOrNull { i -> i >= minParagraph && normalize(text.paragraphs[i]).contains(key) }
                    ?: continue
                return Found(page, paragraph, line.trim())
            }
        }
        return null
    }

    private fun pageBlocks(pages: List<PageText>, bodyStart: Int): List<NotesSection> {
        val sections = ArrayList<NotesSection>()
        if (bodyStart > 0) sections += NotesSection(null, SectionKind.FRONT, 0, 0)
        for (start in bodyStart until pages.size step PAGES_PER_BLOCK) sections += NotesSection(null, SectionKind.BODY, start, 0)
        if (sections.isEmpty()) sections += NotesSection(null, SectionKind.BODY, 0, 0)
        return sections
    }

    private fun looksLikeTitlePage(pages: List<PageText>): Boolean {
        if (pages.size < 3) return false
        val words = pages[0].lines.sumOf { line -> line.split(' ').count { it.isNotBlank() } }
        return words in 1 until TITLE_PAGE_MAX_WORDS
    }

    private fun displayTitle(entry: Entry): String = if (entry.number != null) "${entry.number}. ${entry.title}" else entry.title

    private fun stripNumber(text: String): String = text.trim().replace(Regex("""^\d{1,2}(\.\d{1,2})*\.?\s+"""), "")

    /** Lower case letters and digits, single spaces: "4. Corners, phase & poles" → "4 corners phase poles". */
    internal fun normalize(text: String): String =
        text.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
}
