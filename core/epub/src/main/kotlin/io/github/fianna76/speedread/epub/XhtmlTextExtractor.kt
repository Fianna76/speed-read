package io.github.fianna76.speedread.epub

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeFilter
import org.jsoup.select.NodeFilter.FilterResult

/** Plain text of one content document, split into paragraphs. */
data class ExtractedText(
    val paragraphs: List<String>,
    /** Text of the first heading (h1-h6), useful as a fallback chapter title. */
    val firstHeading: String?,
)

/**
 * Converts an XHTML content document into paragraphs of plain text suitable for RSVP.
 *
 * Block-level elements and `<br>` delimit paragraphs; inline markup is flattened. Non-textual
 * or out-of-flow content is dropped: scripts, styles, SVG/MathML, ruby annotations, page-break
 * markers, navigation blocks, footnote bodies and footnote reference markers.
 */
object XhtmlTextExtractor {

    fun extract(xhtml: ByteArray): ExtractedText {
        val document = parseXml(xhtml)
        val body = document.firstDescendant("body") ?: document
        val collector = ParagraphCollector()
        var firstHeading: String? = null

        body.filter(object : NodeFilter {
            override fun head(node: Node, depth: Int): FilterResult {
                when (node) {
                    is TextNode -> collector.append(node.wholeText)
                    is Element -> {
                        if (node.isSkipped()) return FilterResult.SKIP_ENTIRELY
                        val name = node.localName
                        if (name in BLOCK_ELEMENTS) collector.breakParagraph()
                        if (firstHeading == null && name in HEADINGS) {
                            firstHeading = node.normalizedText().ifEmpty { null }
                        }
                    }
                }
                return FilterResult.CONTINUE
            }

            override fun tail(node: Node, depth: Int): FilterResult {
                if (node is Element && node.localName in BLOCK_ELEMENTS) collector.breakParagraph()
                return FilterResult.CONTINUE
            }
        })

        return ExtractedText(collector.paragraphs(), firstHeading)
    }

    private fun Element.isSkipped(): Boolean =
        localName in SKIPPED_ELEMENTS || semanticTypes.any { it in SKIPPED_SEMANTICS }

    private class ParagraphCollector {
        private val paragraphs = mutableListOf<String>()
        private val current = StringBuilder()

        fun append(text: String) {
            current.append(text)
        }

        fun breakParagraph() {
            if (current.isEmpty()) return
            val text = current.collapseWhitespace()
            if (text.isNotEmpty()) paragraphs += text
            current.setLength(0)
        }

        fun paragraphs(): List<String> {
            breakParagraph()
            return paragraphs
        }
    }

    private val HEADINGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")

    private val BLOCK_ELEMENTS = HEADINGS + setOf(
        "address", "article", "aside", "blockquote", "br", "caption", "center", "dd", "details",
        "dialog", "div", "dl", "dt", "fieldset", "figcaption", "figure", "footer", "form", "header",
        "hgroup", "hr", "li", "main", "ol", "p", "pre", "section", "summary", "table", "tbody", "td",
        "tfoot", "th", "thead", "tr", "ul",
    )

    private val SKIPPED_ELEMENTS = setOf(
        "head", "title", "script", "style", "template", "noscript", "svg", "math", "rt", "rp",
        "nav", "iframe", "object", "video", "audio", "img", "button", "input", "select", "textarea",
    )

    /** `epub:type` / ARIA role values marking content that isn't part of the main text flow. */
    private val SKIPPED_SEMANTICS = setOf(
        "noteref", "footnote", "footnotes", "endnote", "endnotes", "rearnote", "rearnotes", "pagebreak",
        "doc-noteref", "doc-footnote", "doc-endnote", "doc-endnotes", "doc-pagebreak",
    )
}
