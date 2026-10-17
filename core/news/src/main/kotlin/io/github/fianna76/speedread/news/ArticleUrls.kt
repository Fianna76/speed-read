package io.github.fianna76.speedread.news

import java.net.URI
import java.util.Locale

object ArticleUrls {

    /**
     * [url] without the fragment and the campaign-tracking query parameters feeds add (which
     * change between refreshes), so the same story always maps to the same key.
     */
    fun canonical(url: String): String {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return url.trim()
        val query = uri.rawQuery?.split('&')
            ?.filter { param -> param.isNotEmpty() && !isTracking(param.substringBefore('=')) }
            ?.joinToString("&")
            ?.ifEmpty { null }
        return buildString {
            append(uri.scheme?.lowercase(Locale.ROOT) ?: "https").append("://")
            append(uri.rawAuthority?.lowercase(Locale.ROOT).orEmpty())
            append(uri.rawPath.orEmpty())
            if (query != null) append('?').append(query)
        }
    }

    /** The site's host without a leading "www.", for display. */
    fun displayHost(url: String): String =
        runCatching { URI(url).host }.getOrNull()?.removePrefix("www.").orEmpty()

    private fun isTracking(name: String): Boolean {
        val key = name.lowercase(Locale.ROOT)
        return key.startsWith("utm_") || key in TRACKING_PARAMS
    }

    private val TRACKING_PARAMS = setOf(
        "at_medium", "at_campaign", "at_link_origin", "at_ptr_name", "traffic_source", "maca", "cmp", "ns_mchannel",
        "ns_source", "ns_campaign", "ocid", "fbclid", "gclid", "ref", "rss",
    )
}
