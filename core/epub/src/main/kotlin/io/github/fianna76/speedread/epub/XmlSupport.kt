package io.github.fianna76.speedread.epub

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

/*
 * Small namespace-agnostic helpers on top of jsoup's lenient XML parser. Package, NCX and
 * navigation documents are frequently written with arbitrary prefixes (`opf:item`, `dc:title`)
 * and are not always well-formed, so elements are matched on their lowercase local name.
 *
 * jsoup never resolves DTDs or external entities, which keeps parsing safe from XXE attacks.
 */

/** Parses XML bytes, honouring a BOM or the XML declaration's encoding (UTF-8 by default). */
internal fun parseXml(bytes: ByteArray): Document =
    Jsoup.parse(bytes.inputStream(), null, "", Parser.xmlParser())

/** Lowercase tag name without namespace prefix. */
internal val Element.localName: String
    get() = tagName().substringAfterLast(':').lowercase()

/** All descendants (including this element) with the given local name, in document order. */
internal fun Element.descendants(localName: String): List<Element> =
    allElements.filter { it.localName == localName }

internal fun Element.firstDescendant(localName: String): Element? =
    allElements.firstOrNull { it.localName == localName }

internal fun Element.childElements(localName: String): List<Element> =
    children().filter { it.localName == localName }

/** Value of the first attribute whose local name matches, regardless of its prefix. */
internal fun Element.attrLocal(localName: String): String? =
    attributes().firstOrNull { it.key.substringAfterLast(':').equals(localName, ignoreCase = true) }?.value

/** Whitespace-separated tokens of the `epub:type` and ARIA `role` attributes, lowercased. */
internal val Element.semanticTypes: Set<String>
    get() {
        val epubType = attributes().firstOrNull { it.key.endsWith(":type", ignoreCase = true) }?.value.orEmpty()
        val role = attr("role")
        return "$epubType $role".lowercase().split(' ').filterTo(mutableSetOf()) { it.isNotBlank() }
    }

/** Element text with all whitespace runs collapsed to single spaces. */
internal fun Element.normalizedText(): String = text().collapseWhitespace()

// \s alone only covers ASCII whitespace; \p{Z} adds NBSP, thin/em spaces, ideographic space, etc.
private val WHITESPACE_RUN = Regex("[\\s\\p{Z}\\u200B]+")

internal fun CharSequence.collapseWhitespace(): String =
    WHITESPACE_RUN.replace(this, " ").replace("\u00AD", "").replace("\uFEFF", "").trim()
