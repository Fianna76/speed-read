package io.github.fianna76.speedread.rsvp

import kotlin.math.min
import kotlin.math.roundToLong

/** User-tunable playback parameters. */
data class PlaybackSettings(
    val wordsPerMinute: Int = WordsPerMinute.DEFAULT,
    /** Linger on punctuation, long words and numbers so comprehension keeps up with speed. */
    val smartPacing: Boolean = true,
    /** Show the first few words after (re)starting a little longer so the eye can lock on. */
    val rampUp: Boolean = true,
    /** Words flashed together (1 to [Chunking.MAX_SIZE]); see [Chunking]. */
    val chunkSize: Int = 1,
)

/** Supported reading speeds. */
object WordsPerMinute {
    const val MIN = 100
    const val MAX = 1000
    const val STEP = 25
    const val DEFAULT = 300

    fun clamp(wpm: Int): Int = wpm.coerceIn(MIN, MAX)
}

/** Converts words and settings into display durations. */
object Pacing {

    /** Typical mean of [multiplier] over prose with smart pacing on, used for time estimates. */
    private const val AVERAGE_SMART_MULTIPLIER = 1.2

    private const val LONG_WORD_THRESHOLD = 7
    private const val LONG_WORD_EXTRA_PER_LETTER = 0.08
    private const val LONG_WORD_MAX_EXTRA = 0.6
    private const val NUMBER_EXTRA = 0.3

    private val RAMP_UP = doubleArrayOf(2.0, 1.6, 1.3, 1.1)

    /** Milliseconds per word at [wordsPerMinute] before any pacing adjustments. */
    fun baseIntervalMillis(wordsPerMinute: Int): Double = 60_000.0 / wordsPerMinute.coerceAtLeast(1)

    /** How much longer than the base interval [word] stays on screen. */
    fun multiplier(word: RsvpWord, smartPacing: Boolean): Double {
        if (!smartPacing) return 1.0
        var multiplier = when (word.pause) {
            Pause.NONE -> 1.0
            Pause.CLAUSE -> 1.5
            Pause.SENTENCE -> 2.1
            Pause.PARAGRAPH -> 2.6
        }
        val extraLetters = word.letterCount - LONG_WORD_THRESHOLD
        if (extraLetters > 0) multiplier += min(extraLetters * LONG_WORD_EXTRA_PER_LETTER, LONG_WORD_MAX_EXTRA)
        if (word.hasDigits) multiplier += NUMBER_EXTRA
        return multiplier
    }

    /** Extra factor for the [wordsShown]-th word after playback (re)starts. */
    fun rampUpMultiplier(wordsShown: Int): Double = RAMP_UP.getOrElse(wordsShown) { 1.0 }

    fun durationMillis(word: RsvpWord, settings: PlaybackSettings, wordsShown: Int = Int.MAX_VALUE): Long {
        var duration = baseIntervalMillis(settings.wordsPerMinute) * multiplier(word, settings.smartPacing)
        if (settings.rampUp) duration *= rampUpMultiplier(wordsShown)
        return duration.roundToLong()
    }

    /** Approximate time needed to read [wordCount] words with [settings]. */
    fun estimateMillis(wordCount: Int, settings: PlaybackSettings): Long {
        val average = if (settings.smartPacing) AVERAGE_SMART_MULTIPLIER else 1.0
        return (wordCount * baseIntervalMillis(settings.wordsPerMinute) * average).roundToLong()
    }
}
