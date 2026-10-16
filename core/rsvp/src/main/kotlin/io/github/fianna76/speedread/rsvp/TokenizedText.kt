package io.github.fianna76.speedread.rsvp

import java.util.Arrays

/**
 * A run of text (typically one chapter) split into [RsvpWord]s, with sentence and paragraph
 * boundaries indexed for fast navigation.
 */
class TokenizedText internal constructor(
    val words: List<RsvpWord>,
    /** Ascending word indices at which sentences start; always contains 0 when non-empty. */
    private val sentenceStarts: IntArray,
    /** Ascending word indices at which paragraphs start; always contains 0 when non-empty. */
    private val paragraphStarts: IntArray,
) {
    val size: Int get() = words.size

    fun isEmpty(): Boolean = words.isEmpty()

    operator fun get(index: Int): RsvpWord = words[index]

    val sentenceCount: Int get() = sentenceStarts.size

    /** First word of the sentence containing [index]. */
    fun sentenceStartOf(index: Int): Int = floor(sentenceStarts, index)

    /** First word of the sentence after the one containing [index], or `null` in the last sentence. */
    fun nextSentenceStart(index: Int): Int? = higher(sentenceStarts, index)

    /** First word of the paragraph containing [index]. */
    fun paragraphStartOf(index: Int): Int = floor(paragraphStarts, index)

    /** Index just past the last word of the paragraph containing [index]. */
    fun paragraphEndOf(index: Int): Int = higher(paragraphStarts, index) ?: size

    /**
     * Words surrounding [index] within its paragraph, for showing context while paused.
     * At most [before] words precede and [after] words follow the current one.
     */
    fun contextAround(index: Int, before: Int = 30, after: Int = 30): ContextWindow {
        if (isEmpty()) return ContextWindow(emptyList(), -1, truncatedStart = false, truncatedEnd = false)
        val current = index.coerceIn(0, size - 1)
        val paragraphStart = paragraphStartOf(current)
        val paragraphEnd = paragraphEndOf(current)
        val from = maxOf(paragraphStart, current - before)
        val to = minOf(paragraphEnd, current + after + 1)
        return ContextWindow(
            words = words.subList(from, to).map { it.text },
            currentIndex = current - from,
            truncatedStart = from > paragraphStart,
            truncatedEnd = to < paragraphEnd,
        )
    }

    private fun floor(starts: IntArray, index: Int): Int {
        if (starts.isEmpty()) return 0
        val position = Arrays.binarySearch(starts, index)
        return if (position >= 0) starts[position] else starts[maxOf(0, -position - 2)]
    }

    private fun higher(starts: IntArray, index: Int): Int? {
        val position = Arrays.binarySearch(starts, index)
        val next = if (position >= 0) position + 1 else -position - 1
        return starts.getOrNull(next)
    }

    companion object {
        val EMPTY = TokenizedText(emptyList(), IntArray(0), IntArray(0))
    }
}

/** A slice of a paragraph around the current word. */
data class ContextWindow(
    val words: List<String>,
    /** Position of the current word within [words]. */
    val currentIndex: Int,
    val truncatedStart: Boolean,
    val truncatedEnd: Boolean,
)
