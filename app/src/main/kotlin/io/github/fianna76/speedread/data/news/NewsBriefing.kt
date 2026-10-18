package io.github.fianna76.speedread.data.news

import io.github.fianna76.speedread.data.settings.UserPreferences
import io.github.fianna76.speedread.news.Briefing
import io.github.fianna76.speedread.news.FeedItem
import io.github.fianna76.speedread.news.NewsRegion
import io.github.fianna76.speedread.news.NewsSource
import io.github.fianna76.speedread.news.StoryGroup

/** The briefing over [sources]' current headlines: each region's biggest unread stories (see [Briefing]). */
fun NewsFeedState.briefing(
    sources: List<NewsSource>,
    isRead: (FeedItem) -> Boolean,
    perRegion: Int = Briefing.PER_REGION,
    regions: Set<NewsRegion> = NewsRegion.entries.toSet(),
): List<StoryGroup<FeedItem>> {
    val byId = sources.filter { it.region in regions }.associateBy { it.id }
    val headlines = headlines(byId.keys) { id -> byId[id]?.region?.ordinal ?: 0 }
    return Briefing.select(headlines, regionOf = { byId[it]?.region }, isRead = isRead, perRegion = perRegion)
}

/** The briefing as the user set it up. */
fun NewsFeedState.briefing(prefs: UserPreferences, isRead: (FeedItem) -> Boolean): List<StoryGroup<FeedItem>> =
    briefing(prefs.enabledNewsSources, isRead, prefs.briefingPerRegion, prefs.briefingRegions)
