package io.github.fianna76.speedread.rsvp

/** The kind of boundary that follows a word, which determines how long the reader pauses on it. */
enum class Pause {
    NONE,

    /** Comma, semicolon, colon, dash, or terminal punctuation inside a sentence (e.g. dialogue). */
    CLAUSE,

    /** End of a sentence. */
    SENTENCE,

    /** Last word of a paragraph. */
    PARAGRAPH,
}

/** One token flashed on screen during RSVP playback. */
data class RsvpWord(
    /** Display text, including attached punctuation (e.g. `“Hello,`). */
    val text: String,
    /** UTF-16 start index of the optimal recognition point (the letter the eye fixates on). */
    val orpStart: Int,
    /** UTF-16 end index (exclusive) of the ORP, covering surrogate pairs and combining marks. */
    val orpEnd: Int,
    val pause: Pause,
    /** Number of letters and digits; long words get a little extra display time. */
    val letterCount: Int,
    val hasDigits: Boolean,
)
