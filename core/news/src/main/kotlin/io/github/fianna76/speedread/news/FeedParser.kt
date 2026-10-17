package io.github.fianna76.speedread.news

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.net.URI
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** One story from a news feed: everything needed to preview it without loading the page. */
data class FeedItem(
    val sourceId: String,
    val title: String,
    /** Link to the article, as published in the feed. */
    val url: String,
    /** Plain-text teaser, at most [FeedParser.MAX_SUMMARY_CHARS] characters; may be empty. */
    val summary: String,
    val imageUrl: String?,
    /** Publication time in epoch milliseconds, if the feed gives one. */
    val publishedAt: Long?,
    val author: String?,
) {
    /** [url] without tracking parameters; identifies the story across feed refreshes. */
    val canonicalUrl: String get() = ArticleUrls.canonical(url)
}

/**
 * Parses RSS 2.0, RSS 1.0 (RDF) and Atom feeds into [FeedItem]s, in feed order.
 *
 * Items that are videos, audio, photo galleries or live blogs are skipped: there is no article
 * text to speed-read on those pages.
 */
object FeedParser {

    const val MAX_SUMMARY_CHARS = 400

    /** @throws FeedException if [xml] is not a feed. */
    fun parse(xml: String, source: NewsSource, maxItems: Int = Int.MAX_VALUE): List<FeedItem> {
        val document = Jsoup.parse(xml, source.feedUrl, Parser.xmlParser())
        val entries = document.select("item").ifEmpty { document.select("entry") }
        if (entries.isEmpty() && !document.isFeed()) throw FeedException("Not an RSS or Atom feed: ${source.feedUrl}")
        return entries.asSequence()
            .mapNotNull { it.toItem(source) }
            .filterNot { isNonTextUrl(it.url) || NON_TEXT_TITLE.containsMatchIn(it.title) }
            .distinctBy { it.canonicalUrl }
            .take(maxItems)
            .toList()
    }

    private fun Document.isFeed() = selectFirst("rss, feed, rdf|RDF, channel") != null

    private fun Element.toItem(source: NewsSource): FeedItem? {
        val title = childText("title")?.let(::htmlToText)?.takeIf { it.isNotBlank() } ?: return null
        val link = link()?.let { resolve(source.feedUrl, it) }?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            ?: return null
        val descriptionHtml = childText("description") ?: childText("summary") ?: childText("content")
        val contentHtml = childText("content|encoded") ?: childText("content")
        return FeedItem(
            sourceId = source.id,
            title = title,
            url = link,
            summary = summarize(descriptionHtml ?: contentHtml.orEmpty()),
            imageUrl = image(listOfNotNull(descriptionHtml, contentHtml))?.let { resolve(link, it) },
            publishedAt = (childText("pubDate") ?: childText("published") ?: childText("dc|date") ?: childText("updated"))
                ?.let(::parseDate),
            author = (childText("dc|creator") ?: selectFirst("author > name")?.text() ?: childText("author"))
                ?.let(::htmlToText)?.takeIf { it.isNotBlank() && '@' !in it },
        )
    }

    private fun Element.childText(tag: String): String? =
        children().firstOrNull { it.normalName() == tag.replace('|', ':').lowercase(Locale.ROOT) }?.text()?.trim()?.ifEmpty { null }

    private fun Element.link(): String? {
        // RSS: <link>url</link>. Atom: <link rel="alternate" href="url"/> (rel defaults to alternate).
        val links = children().filter { it.normalName() == "link" }
        links.firstOrNull { it.hasAttr("href") && it.attr("rel").ifEmpty { "alternate" } == "alternate" }
            ?.let { return it.attr("href").trim() }
        return links.firstNotNullOfOrNull { it.text().trim().ifEmpty { null } }
            ?: children().firstOrNull { it.normalName() == "guid" && it.attr("isPermaLink") != "false" }?.text()?.trim()
    }

    /** The largest image the item offers: media RSS, an image enclosure, or the first `<img>`. */
    private fun Element.image(htmlBodies: List<String>): String? {
        val media = select("media|content, media|thumbnail")
            .filter { it.hasAttr("url") && (it.normalName() == "media:thumbnail" || it.attr("medium").ifEmpty { "image" } == "image") }
            .filterNot { it.attr("type").let { type -> type.isNotEmpty() && !type.startsWith("image/") } }
            .maxByOrNull { it.attr("width").toIntOrNull() ?: 0 }
        media?.let { return it.attr("url") }
        children().firstOrNull { it.normalName() == "enclosure" && it.attr("type").startsWith("image/") }
            ?.let { return it.attr("url") }
        return htmlBodies.firstNotNullOfOrNull { html -> Jsoup.parse(html).selectFirst("img[src]")?.attr("src") }
    }

    private fun summarize(html: String): String {
        val text = htmlToText(html).replace(CONTINUE_READING, "").trim()
        if (text.length <= MAX_SUMMARY_CHARS) return text
        val cut = text.lastIndexOf(' ', MAX_SUMMARY_CHARS - 1).takeIf { it > MAX_SUMMARY_CHARS / 2 } ?: (MAX_SUMMARY_CHARS - 1)
        return text.substring(0, cut).trimEnd(',', ';', ':', ' ') + "\u2026"
    }

    /** Feed fields are often HTML (sometimes escaped twice); reduce them to one line of text. */
    private fun htmlToText(html: String): String =
        Jsoup.parse(html).text().let { if ('<' in it || '&' in it) Jsoup.parse(it).text() else it }
            .replace(WHITESPACE, " ").trim()

    internal fun parseDate(value: String): Long? {
        val text = value.trim().replace(WHITESPACE, " ")
        for (parse in DATE_PARSERS) {
            try {
                return parse(text)
            } catch (_: DateTimeParseException) {
            }
        }
        return null
    }

    /** True for links to pages that are not articles: video, audio, galleries, live coverage. */
    internal fun isNonTextUrl(url: String): Boolean {
        val path = runCatching { URI(url).path }.getOrNull() ?: return false
        return path.split('/').any { it.lowercase(Locale.ROOT) in NON_TEXT_SEGMENTS }
    }

    private fun resolve(base: String, url: String): String =
        runCatching { URI(base).resolve(url.trim()).toString() }.getOrDefault(url.trim())

    private val WHITESPACE = Regex("\\s+")
    private val CONTINUE_READING = Regex("\\s*Continue reading\\s*(\\.\\.\\.|\u2026)?\\s*$", RegexOption.IGNORE_CASE)

    /** "LIVE: …", "Watch: …", "Video | …": labels for live blogs and media in titles. */
    private val NON_TEXT_TITLE = Regex("^(live|watch|video|listen|podcast|in pictures)\\s*[:|]", RegexOption.IGNORE_CASE)

    private val NON_TEXT_SEGMENTS = setOf(
        "video", "videos", "av", "audio", "podcast", "podcasts", "sounds", "gallery", "galleries",
        "in-pictures", "picture", "pictures", "live", "liveblog", "livestory", "live-news",
    )

    // RFC 822 dates appear with numeric offsets or zone names ("EDT"); Atom uses ISO 8601.
    private val RFC_822_ZONE_NAME = DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm[:ss] zzz", Locale.US)
    private val RFC_822_NO_DAY = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm[:ss] [zzz][Z]", Locale.US)
    private val DATE_PARSERS: List<(String) -> Long> = listOf(
        { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() },
        { ZonedDateTime.parse(it, RFC_822_ZONE_NAME).toInstant().toEpochMilli() },
        { ZonedDateTime.parse(it, RFC_822_NO_DAY).toInstant().toEpochMilli() },
        { OffsetDateTime.parse(it).toInstant().toEpochMilli() },
        { Instant.parse(it).toEpochMilli() },
    )
}

class FeedException(message: String) : Exception(message)
