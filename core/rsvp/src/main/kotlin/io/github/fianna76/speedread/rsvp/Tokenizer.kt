package io.github.fianna76.speedread.rsvp

/**
 * Splits paragraphs of plain text into [RsvpWord]s and detects sentence boundaries.
 *
 * Words are whitespace-separated tokens with their punctuation attached. Tokens joined by an
 * em/en dash (`word—word`) are split after the dash so each half gets its own flash.
 * Sentence detection is heuristic: terminal punctuation ends a sentence unless it belongs to a
 * known abbreviation or initial, or the next word starts in lowercase (e.g. `“Wait!” she said`).
 */
object Tokenizer {

    fun tokenize(paragraphs: List<String>): TokenizedText {
        val words = ArrayList<RsvpWord>()
        val sentenceStarts = ArrayList<Int>()
        val paragraphStarts = ArrayList<Int>()

        for (paragraph in paragraphs) {
            val tokens = splitWords(paragraph)
            if (tokens.isEmpty()) continue
            paragraphStarts += words.size
            sentenceStarts += words.size
            for ((i, token) in tokens.withIndex()) {
                val isLast = i == tokens.lastIndex
                val boundary = if (isLast) Pause.PARAGRAPH else boundaryAfter(token, tokens[i + 1])
                words += createWord(token, boundary)
                if (boundary == Pause.SENTENCE) sentenceStarts += words.size
            }
        }
        return TokenizedText(words, sentenceStarts.toIntArray(), paragraphStarts.toIntArray())
    }

    /** Number of words [tokenize] would produce, without allocating them. */
    fun countWords(paragraphs: List<String>): Int = paragraphs.sumOf { splitWords(it).size }

    internal fun splitWords(paragraph: String): List<String> =
        paragraph.split(WHITESPACE)
            .asSequence()
            .filter { it.isNotEmpty() }
            .map { it.replace("--", "\u2014") }
            .flatMap(::splitAfterDashes)
            .toList()

    private fun splitAfterDashes(token: String): List<String> {
        if (token.none { it == EM_DASH || it == EN_DASH }) return listOf(token)
        val parts = ArrayList<String>(2)
        var start = 0
        for (i in token.indices) {
            val c = token[i]
            // Split only between two non-empty halves, keeping the dash on the left one.
            if ((c == EM_DASH || c == EN_DASH) && i > start && i + 1 < token.length) {
                parts += token.substring(start, i + 1)
                start = i + 1
            }
        }
        parts += token.substring(start)
        return parts
    }

    private fun boundaryAfter(token: String, next: String): Pause {
        val core = token.trimEnd { it in CLOSING_PUNCTUATION }
        val last = core.lastOrNull() ?: return Pause.NONE
        return when {
            last in SENTENCE_TERMINATORS -> when {
                last == '.' && isAbbreviation(core) -> Pause.NONE
                // A lowercase continuation means the sentence goes on: “Wait!” she said.
                next.firstOrNull { it.isLetterOrDigit() }?.isLowerCase() == true -> Pause.CLAUSE
                else -> Pause.SENTENCE
            }
            last in CLAUSE_TERMINATORS -> Pause.CLAUSE
            else -> Pause.NONE
        }
    }

    private fun isAbbreviation(coreWithPeriod: String): Boolean {
        val word = coreWithPeriod.trimStart { !it.isLetterOrDigit() }
        if (INITIAL.matches(word) || DOTTED_ABBREVIATION.matches(word)) return true
        return word.dropLast(1).lowercase() in ABBREVIATIONS
    }

    private fun createWord(token: String, pause: Pause): RsvpWord {
        val orp = OptimalRecognitionPoint.rangeIn(token)
        var letters = 0
        var digits = false
        var i = 0
        while (i < token.length) {
            val cp = token.codePointAt(i)
            if (Character.isLetterOrDigit(cp)) letters++
            if (Character.isDigit(cp)) digits = true
            i += Character.charCount(cp)
        }
        return RsvpWord(
            text = token,
            orpStart = orp.first,
            orpEnd = orp.last + 1,
            pause = pause,
            letterCount = letters,
            hasDigits = digits,
        )
    }

    private const val EM_DASH = '\u2014'
    private const val EN_DASH = '\u2013'

    private val WHITESPACE = Regex("[\\s\\p{Z}\\u200B]+")
    private val INITIAL = Regex("^\\p{L}\\.$")
    private val DOTTED_ABBREVIATION = Regex("^(\\p{L}\\.){2,}$")

    private val SENTENCE_TERMINATORS = setOf('.', '!', '?', '\u2026', '\u203D', '\u3002', '\uFF01', '\uFF1F')
    private val CLAUSE_TERMINATORS = setOf(',', ';', ':', EM_DASH, EN_DASH, '\u2012', '\uFF0C', '\u3001', '\uFF1B', '\uFF1A')
    private val CLOSING_PUNCTUATION = setOf('"', '\'', '\u201D', '\u2019', '\u00BB', '\u203A', ')', ']', '}')

    /** Common English abbreviations that end with a period but rarely end a sentence. */
    private val ABBREVIATIONS = setOf(
        "mr", "mrs", "ms", "mx", "dr", "prof", "sr", "jr", "st", "mt", "ft", "vs", "cf", "approx",
        "vol", "vols", "fig", "figs", "p", "pp", "ch", "chap", "ed", "eds", "gen", "col",
        "capt", "lt", "sgt", "cpl", "maj", "rev", "hon", "gov", "sen", "rep", "pres", "esq", "dept",
        "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "sept", "oct", "nov", "dec",
    )
}
