package io.github.fianna76.speedread.news

import java.util.Locale

/** A story an alert was already posted for, so it is not announced twice. */
data class AlertedStory(val url: String, val title: String)

/**
 * Finds breaking stories among Irish outlets' lead headlines, for opt-in alerts.
 *
 * Top-stories feeds change all the time, so "a new lead story" alone would mean a stream of
 * alerts. A story counts as breaking only when it is fresh (published in the last
 * [MAX_AGE_MILLIS], if the feed gives a time) and either:
 * - it is among the top [TOP_PER_FEED] stories of at least [MIN_OUTLETS] outlets, so several
 *   newsrooms independently rate it a lead; or
 * - an outlet labels it breaking ("Breaking: …").
 *
 * A story already alerted is skipped by URL or by a matching headline, so another outlet's
 * version of it doesn't alert again.
 */
object BreakingNews {

    const val TOP_PER_FEED = 5
    const val MIN_OUTLETS = 2
    const val MAX_AGE_MILLIS = 3 * 60 * 60 * 1000L

    /**
     * @param feeds each outlet's headlines in feed order.
     * @return breaking stories, most widely covered first.
     */
    fun find(
        feeds: Map<String, List<FeedItem>>,
        now: Long,
        alerted: Collection<AlertedStory> = emptyList(),
        isRead: (FeedItem) -> Boolean = { false },
    ): List<StoryGroup<FeedItem>> {
        val tops = feeds.values.map { it.take(TOP_PER_FEED) }
        val ranked = (0 until TOP_PER_FEED).flatMap { round -> tops.mapNotNull { it.getOrNull(round) } }
            .distinctBy { it.canonicalUrl }
        val alertedUrls = alerted.mapTo(HashSet()) { it.url }
        return StoryGrouping.group(ranked, title = { it.title }, outlet = { it.sourceId })
            .filter { group ->
                val versions = listOf(group.lead) + group.related
                val outlets = versions.mapTo(HashSet()) { it.sourceId }.size
                (outlets >= MIN_OUTLETS || versions.any { isLabelledBreaking(it.title) }) &&
                    isFresh(versions, now) &&
                    versions.none { isRead(it) || it.canonicalUrl in alertedUrls } &&
                    alerted.none { old -> versions.any { StoryGrouping.isSameStory(old.title, it.title) } }
            }
            .sortedByDescending { it.related.size }
    }

    fun isLabelledBreaking(title: String): Boolean = BREAKING.containsMatchIn(title.lowercase(Locale.ROOT))

    /** Undated stories count as fresh: the alerted list still stops repeats. */
    private fun isFresh(versions: List<FeedItem>, now: Long): Boolean {
        val newest = versions.mapNotNull { it.publishedAt }.maxOrNull() ?: return true
        return now - newest <= MAX_AGE_MILLIS
    }

    private val BREAKING = Regex("^\\s*(breaking( news)?|just in)\\s*[:|\\-–]")
}
