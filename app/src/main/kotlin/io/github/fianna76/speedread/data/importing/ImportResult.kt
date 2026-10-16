package io.github.fianna76.speedread.data.importing

/** Outcome of importing one file. */
sealed interface ImportResult {
    data class Imported(val bookId: Long, val title: String) : ImportResult

    data class AlreadyInLibrary(val bookId: Long, val title: String) : ImportResult

    data class Failed(val fileName: String?, val reason: ImportError) : ImportResult
}

enum class ImportError {
    /** Not a ZIP/EPUB, or the package structure is broken. */
    NOT_AN_EPUB,

    /** The text is encrypted with DRM. */
    DRM_PROTECTED,

    /** Valid EPUB, but no readable text (e.g. image-only comics). */
    NO_TEXT,

    /** Larger than the import limit. */
    TOO_LARGE,

    /** The file couldn't be read (revoked permission, I/O error, out of space). */
    UNREADABLE,
}
