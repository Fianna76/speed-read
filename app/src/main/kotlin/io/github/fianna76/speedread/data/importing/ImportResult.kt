package io.github.fianna76.speedread.data.importing

/** Outcome of importing one file. */
sealed interface ImportResult {
    data class Imported(val bookId: Long, val title: String) : ImportResult

    data class AlreadyInLibrary(val bookId: Long, val title: String) : ImportResult

    data class Failed(val fileName: String?, val reason: ImportError) : ImportResult
}

enum class ImportError {
    /** Neither an EPUB nor a PDF, or the file's structure is broken. */
    INVALID_FILE,

    /** The text is encrypted with DRM. */
    DRM_PROTECTED,

    /** A PDF that needs a password to open. */
    PASSWORD_PROTECTED,

    /** Valid book, but no readable text (image-only EPUBs, scanned PDFs without a text layer). */
    NO_TEXT,

    /** Larger than the import limit. */
    TOO_LARGE,

    /** The file couldn't be read (revoked permission, I/O error, out of space). */
    UNREADABLE,
}
