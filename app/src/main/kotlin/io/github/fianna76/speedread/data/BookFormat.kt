package io.github.fianna76.speedread.data

import java.io.File

/** Supported book file formats. */
enum class BookFormat(val extension: String) {
    EPUB("epub"),
    PDF("pdf"),
    ;

    companion object {
        private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04) // "PK\u0003\u0004"
        private val PDF_MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)

        /** The PDF spec tolerates junk before the header; readers look within the first 1 KiB. */
        private const val PDF_HEADER_WINDOW = 1024

        /**
         * Identifies a file by its content rather than its name or MIME type, which document
         * providers often get wrong (e.g. reporting EPUBs as `application/octet-stream`).
         */
        fun sniff(file: File): BookFormat? {
            val header = ByteArray(PDF_HEADER_WINDOW)
            val length = file.inputStream().use { input -> input.read(header).coerceAtLeast(0) }
            return when {
                header.startsWith(ZIP_MAGIC, length) -> EPUB
                header.contains(PDF_MAGIC, length) -> PDF
                else -> null
            }
        }

        /** Parses a stored format name, treating unknown values as EPUB (the original format). */
        fun fromStored(name: String): BookFormat = entries.firstOrNull { it.name == name } ?: EPUB

        private fun ByteArray.startsWith(prefix: ByteArray, length: Int): Boolean =
            length >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

        private fun ByteArray.contains(needle: ByteArray, length: Int): Boolean =
            (0..length - needle.size).any { start -> needle.indices.all { this[start + it] == needle[it] } }
    }
}
