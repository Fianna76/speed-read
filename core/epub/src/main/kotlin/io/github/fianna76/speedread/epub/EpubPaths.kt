package io.github.fianna76.speedread.epub

import java.io.ByteArrayOutputStream

/** A reference resolved against the archive root, split into document path and fragment. */
internal data class ResolvedHref(val path: String, val fragment: String?)

/**
 * Helpers for EPUB-internal references. Hrefs in package, navigation and content documents are
 * percent-encoded URLs relative to the referencing document, while ZIP entries use plain paths.
 */
internal object EpubPaths {

    private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

    /** Resolves [href] found in the document at [referencingPath]. Returns `null` for external links. */
    fun resolve(referencingPath: String, href: String): ResolvedHref? {
        val trimmed = href.trim()
        if (trimmed.isEmpty() || SCHEME.containsMatchIn(trimmed)) return null

        val fragment = trimmed.substringAfter('#', missingDelimiterValue = "").ifEmpty { null }
        val pathPart = trimmed.substringBefore('#').substringBefore('?')
        if (pathPart.isEmpty()) return ResolvedHref(referencingPath, fragment)

        val decoded = percentDecode(pathPart)
        val combined = if (decoded.startsWith('/')) decoded else parentOf(referencingPath) + decoded
        return ResolvedHref(normalize(combined), fragment)
    }

    /** Directory of [path] including the trailing slash, or "" for entries at the archive root. */
    fun parentOf(path: String): String {
        val index = path.lastIndexOf('/')
        return if (index < 0) "" else path.substring(0, index + 1)
    }

    /** Collapses `.`/`..` segments, duplicate and leading slashes, and Windows separators. */
    fun normalize(path: String): String {
        val segments = ArrayDeque<String>()
        for (segment in path.replace('\\', '/').split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> segments.removeLastOrNull()
                else -> segments.addLast(segment)
            }
        }
        return segments.joinToString("/")
    }

    /** Decodes `%XX` escapes as UTF-8. Unlike URLDecoder, `+` is left untouched. */
    fun percentDecode(value: String): String {
        if ('%' !in value) return value
        val bytes = ByteArrayOutputStream(value.length)
        val literal = StringBuilder()
        fun flushLiteral() {
            if (literal.isNotEmpty()) {
                bytes.write(literal.toString().toByteArray(Charsets.UTF_8))
                literal.setLength(0)
            }
        }
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '%' && i + 2 < value.length) {
                val high = Character.digit(value[i + 1], 16)
                val low = Character.digit(value[i + 2], 16)
                if (high >= 0 && low >= 0) {
                    flushLiteral()
                    bytes.write(high * 16 + low)
                    i += 3
                    continue
                }
            }
            literal.append(c)
            i++
        }
        flushLiteral()
        return bytes.toString(Charsets.UTF_8.name())
    }
}
