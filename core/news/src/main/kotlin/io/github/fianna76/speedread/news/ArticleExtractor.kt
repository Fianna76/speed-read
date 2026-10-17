package io.github.fianna76.speedread.news

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

/** The readable content of a web article. */
data class ExtractedArticle(
    val title: String,
    val imageUrl: String?,
    /** Body text, one entry per paragraph or subheading, with whitespace normalised. */
    val paragraphs: List<String>,
) {
    val wordCount: Int get() = paragraphs.sumOf { p -> p.split(' ').count { it.isNotEmpty() } }
}

open class ArticleExtractionException(message: String) : Exception(message)

/** The page declares itself subscriber-only; its text is not extracted even if present. */
class PaywalledArticleException(url: String) : ArticleExtractionException("Subscriber-only article: $url")

/**
 * Pulls the story text out of a news article page, like a browser's reader mode.
 *
 * Page chrome (navigation, sharing buttons, related links, captions, adverts) is stripped
 * first. The body is then the source's [NewsSource.bodySelector] when that yields enough text,
 * otherwise the element whose paragraphs score highest, where paragraphs count towards their
 * parent and, at half weight, their grandparent (so a story split over many small blocks still
 * gathers at the container that holds them all).
 */
object ArticleExtractor {

    /** Fewer words than this means a teaser, a paywall or a non-article page. */
    const val MIN_WORDS = 60

    /**
     * @throws PaywalledArticleException when the page marks its content as not free.
     * @throws ArticleExtractionException when the page has no recognisable article text.
     */
    fun extract(html: String, url: String, bodySelector: String? = null): ExtractedArticle {
        val document = Jsoup.parse(html, url)
        if (isPaywalled(document)) throw PaywalledArticleException(url)
        val title = withoutSiteName(
            metaContent(document, "og:title", "twitter:title")
                ?: document.selectFirst("h1")?.text()?.trim()?.ifEmpty { null }
                ?: document.title().trim(),
            siteName = metaContent(document, "og:site_name"),
        )
        val imageUrl = metaContent(document, "og:image", "twitter:image")
            ?.let { runCatching { URI(url).resolve(it).toString() }.getOrNull() }
            ?.takeIf { it.startsWith("http") }

        removeClutter(document)
        val body = bodySelector?.let { hinted(document, it) } ?: bestCandidate(document)
            ?: throw ArticleExtractionException("No article text found at $url")
        val paragraphs = paragraphsOf(body, title)
        val article = ExtractedArticle(title = title, imageUrl = imageUrl, paragraphs = paragraphs)
        if (article.wordCount < MIN_WORDS) throw ArticleExtractionException("Only ${article.wordCount} words found at $url")
        return article
    }

    /**
     * Publishers mark subscriber-only articles with schema.org's `isAccessibleForFree: false`
     * (the convention search engines use for paywalls). Such pages often ship the full text
     * for subscribers' browsers to reveal; the app respects the paywall and links out instead.
     */
    fun isPaywalled(document: Document): Boolean =
        document.select("script[type=application/ld+json]").any { NOT_FREE.containsMatchIn(it.data()) }

    // --- Cleaning ----------------------------------------------------------------------------

    private fun removeClutter(document: Document) {
        document.select(CLUTTER_TAGS).remove()
        document.select("[hidden], [aria-hidden=true], [role=navigation], [role=banner], [role=contentinfo], [role=complementary]")
            .remove()
        // Labels meant only for screen readers ("Published", "Image source") read as stray words.
        document.select("[class~=(?i)(visually-hidden|visuallyhidden|sr-only|screen-reader)]").remove()
        document.body().getAllElements()
            .filter { it !== document.body() && isUnlikely(it) }
            .forEach { it.remove() }
    }

    private fun isUnlikely(element: Element): Boolean {
        val names = element.className() + " " + element.id() + " " + element.attr("data-component") + " " +
            element.attr("data-testid")
        if (names.isBlank() || !UNLIKELY.containsMatchIn(names) || MAYBE.containsMatchIn(names)) return false
        // Layout wrappers are sometimes named after a sidebar or menu they sit beside.
        return element.normalName() != "article" && element.selectFirst(MAIN_CONTENT) == null
    }

    // --- Choosing the body -------------------------------------------------------------------

    private fun hinted(document: Document, selector: String): Element? =
        runCatching { document.select(selector) }.getOrDefault(emptyList())
            .maxByOrNull { paragraphLength(it) }
            ?.takeIf { paragraphsOf(it, title = null).sumOf { p -> p.split(' ').size } >= MIN_WORDS }

    private fun bestCandidate(document: Document): Element? {
        val scores = HashMap<Element, Double>()
        for (paragraph in document.select("p")) {
            val text = paragraph.text()
            if (text.length < MIN_PARAGRAPH_CHARS) continue
            val score = 1.0 + text.count { it == ',' } + minOf(text.length / 100.0, 3.0)
            val parent: Element = paragraph.parent() ?: continue
            scores.merge(parent, score, Double::plus)
            val grandparent: Element? = parent.parent()
            if (grandparent != null) scores.merge(grandparent, score / 2, Double::plus)
        }
        val top = scores.maxByOrNull { (element, score) -> score * (1 - linkDensity(element)) }?.key ?: return null
        // A story whose paragraphs are wrapped block by block peaks at a single block: widen to
        // the ancestor that gathers clearly more of the story's paragraphs.
        var best: Element = top
        var bestLength = paragraphLength(top)
        var ancestor: Element? = top.parent()
        repeat(MAX_WIDEN_LEVELS) {
            if (ancestor == null || ancestor === document.body()) return best
            val length = paragraphLength(ancestor)
            if (length >= bestLength * WIDEN_FACTOR && linkDensity(ancestor) < MAX_LINK_DENSITY / 2) {
                best = ancestor
                bestLength = length
            }
            ancestor = ancestor.parent()
        }
        return best
    }

    private fun paragraphLength(element: Element): Int =
        element.select("p").sumOf { p -> p.text().length.takeIf { it >= MIN_PARAGRAPH_CHARS } ?: 0 }

    private fun linkDensity(element: Element): Double {
        val length = element.text().length
        if (length == 0) return 0.0
        return element.select("a").sumOf { it.text().length }.toDouble() / length
    }

    // --- Collecting text ---------------------------------------------------------------------

    private fun paragraphsOf(body: Element, title: String?): List<String> {
        val blocks = body.select(BLOCK_TAGS).filter { block ->
            // Take the innermost blocks only: a quote made of paragraphs yields those paragraphs.
            block.select(BLOCK_TAGS).size == 1
        }
        val result = ArrayList<Pair<String, Boolean>>() // Text, and whether it is a heading.
        for (block in blocks) {
            val text = block.text().replace(WHITESPACE, " ").trim()
            if (text.isEmpty() || text == title) continue
            if (block.normalName() == "li" && text.length < MIN_PARAGRAPH_CHARS) continue
            if (linkDensity(block) > MAX_LINK_DENSITY || block.closest("a") != null) continue
            if (BOILERPLATE.containsMatchIn(text)) continue
            if (result.lastOrNull()?.first != text) result += text to block.normalName().matches(HEADING)
        }
        // Drop headings left dangling at the end (e.g. "More on this story").
        while (result.lastOrNull()?.second == true) result.removeAt(result.lastIndex)
        return result.map { it.first }
    }

    /** Strips an appended outlet name: "Story title | CBC News" becomes "Story title". */
    private fun withoutSiteName(title: String, siteName: String?): String {
        val match = SITE_NAME_SUFFIX.find(title) ?: return title
        val suffix = match.groupValues[1].trim()
        val isSiteName = suffix.equals(siteName, ignoreCase = true) || suffix.contains("news", ignoreCase = true)
        return if (isSiteName) title.substring(0, match.range.first) else title
    }

    private fun metaContent(document: Document, vararg names: String): String? = names.firstNotNullOfOrNull { name ->
        document.selectFirst("meta[property=$name], meta[name=$name]")?.attr("content")?.trim()?.ifEmpty { null }
    }

    private const val MIN_PARAGRAPH_CHARS = 25
    private const val MAX_LINK_DENSITY = 0.5
    private const val MAX_WIDEN_LEVELS = 5
    private const val WIDEN_FACTOR = 1.25

    private const val CLUTTER_TAGS =
        "script, style, noscript, template, svg, canvas, iframe, object, embed, form, button, input, select, " +
            "nav, footer, aside, figure, figcaption, picture, video, audio, dialog, menu"
    private const val MAIN_CONTENT = "article, main, [role=main], [itemprop=articleBody]"
    private const val BLOCK_TAGS = "p, h2, h3, h4, blockquote, li, pre"
    private val HEADING = Regex("h[2-4]")
    private val NOT_FREE = Regex("\"isAccessibleForFree\"\\s*:\\s*\"?false\"?", RegexOption.IGNORE_CASE)
    /** " | CBC News", " - BBC News": the outlet's name appended to a page title. */
    private val SITE_NAME_SUFFIX = Regex("\\s+[|\u2013\u2014-]\\s+([^|\u2013\u2014-]{2,30})$")
    private val WHITESPACE = Regex("\\s+")

    private val UNLIKELY = Regex(
        "-ad-|^ad-|\\bads?\\b|adblock|advert|banner|bucket|embed|invite|player|video|breadcrumb|comment|community|cookie|consent|disqus|gdpr|footer|" +
            "masthead|menu|modal|newsletter|outbrain|pagination|pager|popup|promo|recirc|related|remark|replies|similar|" +
            "share|sharing|sidebar|signup|skyscraper|social|sponsor|subscribe|supplemental|taboola|toolbar|" +
            "most-?(read|popular|viewed)|byline|caption|credit|metadata|timestamp|dateline|tags?\\b|topics?\\b|read-?more|more-?on",
        RegexOption.IGNORE_CASE,
    )
    private val MAYBE = Regex("article|body|content|main|story|text", RegexOption.IGNORE_CASE)

    /** Lines that are page furniture rather than story, wherever they appear. */
    private val BOILERPLATE = Regex(
        listOf(
            // Short labels and page furniture.
            "^(advertisement|sponsored|related( articles?| stories?)?|read more|more on this story|share( this)?|" +
                "listen to this article|watch( \\||:)?|image (source|caption)|getty images|edited by:?)\\b.{0,80}$",
            "^(copyright\\s*(\u00A9|\\d{4})|all rights reserved|\u00A9).{0,120}$", "all rights reserved\\W*(\u00A9.*)?$",
            "^\\w{1,4}:$", // A dangling label such as "In:".
            "^last update(d)?\\b", "^(main image|pic|photo|picture):", "^in [\\w ,&]+ - \\d{1,2} \\w{3} \\d{4}",
            "^\\S+@\\S+\\.\\w+( \\(end\\))?$", // A reporter's address, as in "name@agency.com (END)".
            "^the views expressed (here|in this)",
            // Newsletter, social and subscription prompts.
            "^(follow us|sign up|subscribe)\\b.{0,200}$", "^follow .{0,60} on (facebook|x|twitter|instagram|bluesky)\\b",
            "sign up for (our|the) .{0,80}newsletter", "please subscribe to our", "^already a subscriber",
            "^a weekly e-?mail\\b", "^don.t miss out\\b", "^for the latest from\\b", "^everything from .{0,60} is available",
            "^tell us (which|what) stories", "^get in touch$", "^listen to .{0,100} on (bbc )?sounds",
            // Donation and support pleas.
            "^don't let the algorithm", "^support trusted journalism", "^by supporting .{0,80}, you('re| are)\\b", "^keep our news free\\b",
            // Video players.
            "browser extensions? (seems? to be|is) blocking",
        ).joinToString("|"),
        RegexOption.IGNORE_CASE,
    )
}
