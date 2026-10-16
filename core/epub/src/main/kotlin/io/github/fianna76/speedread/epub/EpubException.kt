package io.github.fianna76.speedread.epub

import java.io.IOException

/** Reasons an EPUB can't be opened. Each subtype maps to a distinct, user-presentable error. */
sealed class EpubException(message: String, cause: Throwable? = null) : IOException(message, cause) {

    /** The file is not a ZIP archive or lacks the mandatory EPUB container structure. */
    class NotAnEpub(message: String, cause: Throwable? = null) : EpubException(message, cause)

    /** The container is an EPUB but required documents are missing or unreadable. */
    class Malformed(message: String, cause: Throwable? = null) : EpubException(message, cause)

    /** Content documents are encrypted (Adobe ADEPT, Readium LCP, ...), so the text can't be read. */
    class DrmProtected : EpubException("The book's content is encrypted (DRM).")

    /** An entry exceeds the safety limits used to guard against ZIP bombs. */
    class TooLarge(message: String) : EpubException(message)
}
