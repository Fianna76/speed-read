package io.github.fianna76.speedread.news

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.net.URI
import java.security.MessageDigest

/**
 * Turns what a user types when adding a feed (a feed address, or just a site's address) into
 * a feed: pages advertise their feeds with `<link rel="alternate" type="application/rss+xml">`,
 * and a few common feed paths are tried when they don't.
 */
object FeedDiscovery {

    private val FEED_TYPES = setOf("application/rss+xml", "application/atom+xml", "application/rdf+xml", "application/feed+json")

    /** Paths tried, in order, when a page advertises no feed. */
    val COMMON_PATHS = listOf("/feed", "/rss", "/feed.xml", "/rss.xml", "/atom.xml", "/index.xml")

    /** Adds `https://` to a bare address ("example.com/news"); `null` if it isn't a web address. */
    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || ' ' in trimmed) return null
        val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrEmpty() || '.' !in uri.host) return null
        return withScheme
    }

    fun isFeed(text: String): Boolean {
        val head = text.trimStart().take(1_000).lowercase()
        return "<rss" in head || "<feed" in head || "<rdf:rdf" in head
    }

    /** Feeds a web page advertises, absolute, in page order (RSS and Atom only). */
    fun advertisedFeeds(html: String, pageUrl: String): List<String> =
        Jsoup.parse(html, pageUrl).select("link[rel~=(?i)alternate][href]")
            .filter { it.attr("type").lowercase() in FEED_TYPES && !it.attr("type").contains("json") }
            .map { it.absUrl("href") }
            .filter { it.startsWith("http") }
            .distinct()

    /** The feed's own title (`<channel><title>` or Atom `<title>`), if it has one. */
    fun title(xml: String): String? {
        val document = Jsoup.parse(xml, "", Parser.xmlParser())
        val title = document.selectFirst("channel > title")?.text()
            ?: document.selectFirst("feed > title")?.text()
        return title?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** The site a feed belongs to: its `<link>`, else the feed's own host. */
    fun homepage(xml: String, feedUrl: String): String {
        val document = Jsoup.parse(xml, feedUrl, Parser.xmlParser())
        val link = document.selectFirst("channel > link")?.text()?.trim()?.takeIf { it.startsWith("http") }
            ?: document.selectFirst("feed > link[rel=alternate], feed > link:not([rel])")?.attr("href")?.takeIf { it.startsWith("http") }
        return link ?: URI(feedUrl).let { "${it.scheme}://${it.host}" }
    }

    /** A stable source id for a user-added feed. */
    fun sourceId(feedUrl: String): String =
        CUSTOM_PREFIX + MessageDigest.getInstance("SHA-256").digest(feedUrl.toByteArray()).joinToString("") { "%02x".format(it) }.take(12)

    const val CUSTOM_PREFIX = "custom-"
}
