package io.github.fianna76.studypack.importing

import io.github.fianna76.studypack.quiz.PackParser

enum class FileKind { PACK, PDF, UNKNOWN }

/**
 * Tells packs from PDFs by their first bytes, because cloud apps often send `.json` files as
 * `application/octet-stream` or `text/plain`: `%PDF` means PDF, `{` means JSON. The name and
 * MIME type only break ties when the content says nothing.
 */
object FileSniffer {

    /** How many leading bytes [kind] looks at. PDF readers accept the header anywhere in the first 1 KB. */
    const val HEAD_SIZE = 1024

    private val PDF_MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)

    fun kind(head: ByteArray, fileName: String?, mimeType: String?): FileKind {
        if (indexOf(head, PDF_MAGIC) >= 0) return FileKind.PDF
        if (PackParser.looksLikeJson(head)) return FileKind.PACK
        val name = fileName.orEmpty().lowercase()
        return when {
            head.isEmpty() && (name.endsWith(".json") || mimeType == "application/json") -> FileKind.PACK
            else -> FileKind.UNKNOWN
        }
    }

    private fun indexOf(data: ByteArray, pattern: ByteArray): Int {
        outer@ for (i in 0..data.size - pattern.size) {
            for (j in pattern.indices) if (data[i + j] != pattern[j]) continue@outer
            return i
        }
        return -1
    }
}
