package io.github.fianna76.studypack.data.pdf

import io.github.fianna76.studypack.data.pdf.SectionKind.BACK
import io.github.fianna76.studypack.data.pdf.SectionKind.BODY
import io.github.fianna76.studypack.data.pdf.SectionKind.FRONT
import org.junit.Assert.assertEquals
import org.junit.Test

class NotesStructureTest {

    private val footer = "EE5105 · ECG amplifier overview"

    /** A page whose lines are its paragraphs (headings are their own paragraph), with a running footer and page number. */
    private fun page(number: Int, vararg paragraphs: String) =
        PageText(lines = paragraphs.toList() + footer + "$number", paragraphs = paragraphs.toList() + footer)

    private val titlePage = page(
        1,
        "EE5105 · Applied Circuit Design for Medical Electronics",
        "ECG Amplifier: Single-Supply Bench Demo",
        "Prepared 18 Nov 2026 · Sources: handout, lab notes",
    )

    private fun contentsPage(withPageNumbers: Boolean = true) = page(
        2,
        *listOf(
            "Contents",
            "1. The big picture" to 3,
            "2. Requirements" to 3,
            "3. CMRR" to 4,
            "3.1 Mechanisms" to 4,
            "4. Drill sheet" to 5,
            "5. Formula sheet" to 6,
            "Sources" to 6,
        ).map { if (it is Pair<*, *>) (if (withPageNumbers) "${it.first} ........ ${it.second}" else "${it.first}") else it as String }
            .toTypedArray(),
    )

    private val body = listOf(
        page(3, "1. The big picture", "The amplifier is a three-op-amp instrumentation amp.", "Its signal path:",
            "1. Electrodes", "2. Stage 1 gain", "2. Requirements", "The gain must be about 130."),
        page(4, "3. CMRR", "CMRR is limited by three mechanisms.", "3.1 Mechanisms", "Time-constant mismatch at low frequency."),
        page(5, "4. Drill sheet", "Why does CMRR peak near 5 Hz? Mismatch below, stray capacitance above."),
        page(6, "5. Formula sheet", "τ = RC; G = 1 + 2R2/R1", "Sources", "Annotated design reference; lab handout."),
    )

    private fun sections(pages: List<PageText>, outline: List<OutlineMark> = emptyList()) =
        NotesStructure.detect(pages, outline).map { Triple(it.title, it.kind, it.startPage to it.startParagraph) }

    @Test
    fun overviewLayoutFromTheContentsPage() {
        assertEquals(
            listOf(
                Triple(null, FRONT, 0 to 0),
                Triple("1. The big picture", BODY, 2 to 0),
                Triple("2. Requirements", BODY, 2 to 5), // not the "2. Stage 1 gain" list item before it
                Triple("3. CMRR", BODY, 3 to 0),
                Triple("4. Drill sheet", BODY, 4 to 0),
                Triple("5. Formula sheet", BODY, 5 to 0),
                Triple("Sources", BACK, 5 to 2),
            ),
            sections(listOf(titlePage, contentsPage()) + body),
        )
    }

    @Test
    fun contentsWithoutPageNumbers() {
        // Chrome's print-to-PDF can't fill in page numbers, so entries are titles only.
        val result = sections(listOf(titlePage, contentsPage(withPageNumbers = false)) + body)
        assertEquals(listOf(null, "1. The big picture", "2. Requirements", "3. CMRR", "4. Drill sheet", "5. Formula sheet", "Sources"), result.map { it.first })
        assertEquals(BACK, result.last().second)
    }

    @Test
    fun headingMergedIntoItsParagraph() {
        // The text extractor sometimes joins a heading with the paragraph after it.
        val merged = body.toMutableList()
        merged[1] = PageText(
            lines = listOf("3. CMRR", "CMRR is limited by three mechanisms.", footer, "4"),
            paragraphs = listOf("3. CMRR CMRR is limited by three mechanisms.", footer),
        )
        val result = sections(listOf(titlePage, contentsPage()) + merged)
        assertEquals(Triple("3. CMRR", BODY, 3 to 0), result[3])
    }

    @Test
    fun runningFooterIsNotTakenForAnEntryOrHeading() {
        val result = sections(listOf(titlePage, contentsPage()) + body)
        assert(result.none { it.first == footer })
    }

    @Test
    fun bookmarksWhenThereIsNoContentsPage() {
        val outline = listOf(OutlineMark(2, "1. The big picture"), OutlineMark(3, "3. CMRR"), OutlineMark(5, "Sources"))
        val result = sections(listOf(titlePage, page(2, "A short preface with a few words in it.")) + body, outline)
        assertEquals(
            listOf(
                Triple(null, FRONT, 0 to 0),
                Triple("1. The big picture", BODY, 2 to 0),
                Triple("3. CMRR", BODY, 3 to 0),
                Triple("Sources", BACK, 5 to 2),
            ),
            result,
        )
    }

    @Test
    fun numberedHeadingsWhenThereIsNothingElse() {
        val pages = listOf(
            titlePage,
            page(2, "1. Ohm's law", "V equals I times R, so a 12 V supply over 3 mA means 4 kΩ."),
            page(3, "2. Series and parallel", "In series the current is the same. Steps:", "3. measure it."),
            page(4, "3. RC circuits", "Tau is RC.", "References", "A textbook."),
        )
        assertEquals(
            listOf(
                Triple(null, FRONT, 0 to 0),
                Triple("1. Ohm's law", BODY, 1 to 0),
                Triple("2. Series and parallel", BODY, 2 to 0),
                Triple("3. RC circuits", BODY, 3 to 0),
                Triple("References", BACK, 3 to 2),
            ),
            sections(pages),
        )
    }

    @Test
    fun noStructureSplitsIntoPageBlocks() {
        val pages = List(12) { PageText(listOf("Some running text on page ${it + 1} with enough words to not be a title page " + "word ".repeat(100)), listOf("Text ${it + 1}")) }
        val result = NotesStructure.detect(pages)
        assertEquals(listOf(0, 5, 10), result.map { it.startPage })
        assert(result.all { it.kind == BODY && it.title == null })
    }

    @Test
    fun shortDocumentWithoutStructureIsOneSection() {
        val pages = listOf(PageText(listOf("Hello"), listOf("Hello")))
        assertEquals(listOf(NotesSection(null, BODY, 0, 0)), NotesStructure.detect(pages))
    }

    @Test
    fun contentsOnTheTitlePage() {
        val combined = page(1, "ECG Amplifier Overview", "Contents", "1. The big picture ...... 2", "3. CMRR ...... 3")
        val result = sections(listOf(combined) + body.take(2))
        assertEquals(listOf(null, "1. The big picture", "3. CMRR"), result.map { it.first })
        assertEquals(FRONT, result.first().second)
    }
}
