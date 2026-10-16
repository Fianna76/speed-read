package io.github.fianna76.speedread.rsvp

/**
 * Locates the optimal recognition point (ORP): the letter, slightly left of a word's centre,
 * where the eye recognises the whole word fastest. RSVP readers pin this letter to a fixed
 * position on screen so the eye never has to move.
 *
 * Only letters and digits are counted, so punctuation (`“Hello,`), apostrophes and hyphens don't
 * shift the fixation point. Indices are UTF-16 offsets into the original string; surrogate
 * pairs and trailing combining marks (decomposed accents) are kept together.
 */
object OptimalRecognitionPoint {

    /** Returns the `[start, end)` UTF-16 range of the ORP within [word]. */
    fun rangeIn(word: String): IntRange {
        if (word.isEmpty()) return IntRange.EMPTY
        val letterOffsets = ArrayList<Int>(word.length)
        var i = 0
        while (i < word.length) {
            val cp = word.codePointAt(i)
            if (Character.isLetterOrDigit(cp)) letterOffsets += i
            i += Character.charCount(cp)
        }
        val start = if (letterOffsets.isEmpty()) {
            // Pure punctuation or symbols: fixate on the middle code point.
            word.offsetByCodePoints(0, word.codePointCount(0, word.length) / 2)
        } else {
            letterOffsets[offsetForLength(letterOffsets.size)]
        }
        var end = start + Character.charCount(word.codePointAt(start))
        while (end < word.length && isCombiningMark(word.codePointAt(end))) {
            end += Character.charCount(word.codePointAt(end))
        }
        return start until end
    }

    /** Spritz-style lookup: the fixation point moves right by one letter every four letters. */
    internal fun offsetForLength(length: Int): Int = when {
        length <= 1 -> 0
        length <= 5 -> 1
        length <= 9 -> 2
        length <= 13 -> 3
        else -> 4
    }

    private fun isCombiningMark(codePoint: Int): Boolean = when (Character.getType(codePoint)) {
        Character.NON_SPACING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt(),
        -> true
        else -> false
    }
}
