package io.github.fianna76.speedread.rsvp

/**
 * Chunk mode: flash short phrases ("in the", "of a house") instead of single words, which
 * keeps high speeds readable because the eye takes in a few short words at a glance.
 *
 * A chunk starts at a word and adds the following words while:
 * - it holds fewer than the chunk size;
 * - the last word added ends without a pause (chunks never cross commas or sentence ends);
 * - it stays within [MAX_LETTERS] letters, so long words still appear on their own.
 *
 * The reading speed is unchanged: a chunk stays on screen for as long as its words would
 * have, one by one.
 */
object Chunking {

    const val MAX_SIZE = 3
    const val MAX_LETTERS = 16

    /** Number of words in the chunk starting at [index] (1 when [chunkSize] is 1). */
    fun span(text: TokenizedText, index: Int, chunkSize: Int): Int {
        if (index !in 0 until text.size) return 1
        var span = 1
        var letters = text[index].letterCount
        while (span < chunkSize.coerceIn(1, MAX_SIZE)) {
            val last = text[index + span - 1]
            val next = text.words.getOrNull(index + span) ?: break
            if (last.pause != Pause.NONE || letters + next.letterCount > MAX_LETTERS) break
            letters += next.letterCount
            span++
        }
        return span
    }

    /** The words `[index, index + span)` as one display token, with its focus point near the middle. */
    fun join(text: TokenizedText, index: Int, span: Int): RsvpWord {
        if (span <= 1) return text[index]
        val words = text.words.subList(index, index + span)
        val joined = words.joinToString(" ") { it.text }
        val letterOffsets = joined.indices.filter { Character.isLetterOrDigit(joined[it]) }
        // A phrase is fixated a little left of its centre, like a long word.
        val focus = letterOffsets.getOrNull((letterOffsets.size * FOCUS_FRACTION).toInt()) ?: (joined.length / 2)
        return RsvpWord(
            text = joined,
            orpStart = focus,
            orpEnd = focus + 1,
            pause = words.last().pause,
            letterCount = words.sumOf { it.letterCount },
            hasDigits = words.any { it.hasDigits },
        )
    }

    private const val FOCUS_FRACTION = 0.4
}
