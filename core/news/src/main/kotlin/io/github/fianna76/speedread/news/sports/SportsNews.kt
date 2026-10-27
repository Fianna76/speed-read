package io.github.fianna76.speedread.news.sports

import io.github.fianna76.speedread.news.FeedItem
import io.github.fianna76.speedread.news.NewsRegion
import io.github.fianna76.speedread.news.NewsSource
import java.net.URI
import java.security.MessageDigest

/**
 * Which feeds the Sports section reads, and which followed teams or series each story is about.
 * NBA stories come from league-wide feeds and are matched to teams by name; motorsport
 * stories come from per-series feeds (feeds shared by several series are matched by name).
 */
object SportsNews {

    /** League-wide NBA feeds. */
    val NBA_FEEDS: List<SeriesFeed> = listOf(
        SeriesFeed("https://www.espn.com/espn/rss/nba/news", "ESPN"),
        SeriesFeed("https://www.cbssports.com/rss/headlines/nba/", "CBS Sports"),
        SeriesFeed("https://sports.yahoo.com/nba/rss.xml", "Yahoo Sports"),
        SeriesFeed("https://www.hoopsrumors.com/feed", "Hoops Rumors"),
    )

    /** Feeds to fetch for what is followed, as news sources (one per feed address). */
    fun sources(teams: Set<String>, series: Set<String>): List<NewsSource> {
        val feeds = (if (teams.isEmpty()) emptyList() else NBA_FEEDS) +
            series.mapNotNull(MotorsportCatalog::byId).flatMap { it.news }
        return feeds.distinctBy { it.url }.map(::source)
    }

    /**
     * What [item] is about among [teams] and [series] (team abbreviations and series ids);
     * empty if none, in which case the story isn't shown.
     */
    fun follows(item: FeedItem, teams: Set<String>, series: Set<String>): Set<String> {
        val text = "${item.title} ${item.summary}"
        val result = HashSet<String>()
        if (NBA_FEEDS.any { sourceId(it.url) == item.sourceId }) {
            NbaTeams.ALL.filter { it.abbreviation in teams && it.isMentionedIn(text) }.mapTo(result) { it.abbreviation }
        }
        for (id in series) {
            val feeds = MotorsportCatalog.byId(id)?.news.orEmpty()
            if (feeds.any { sourceId(it.url) == item.sourceId && it.matches(text) }) result += id
        }
        return result
    }

    fun source(feed: SeriesFeed): NewsSource = NewsSource(
        id = sourceId(feed.url),
        name = feed.outlet,
        feedUrl = feed.url,
        homepage = URI(feed.url).let { "${it.scheme}://${it.host}" },
        region = NewsRegion.WORLD,
    )

    /** A stable source id for a sports feed (stories keep it in the reading history). */
    fun sourceId(url: String): String =
        "sport-" + MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }.take(12)
}
