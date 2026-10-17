package io.github.fianna76.speedread.data.news

import io.github.fianna76.speedread.news.Briefing
import io.github.fianna76.speedread.news.FeedItem
import io.github.fianna76.speedread.news.NewsSource
import io.github.fianna76.speedread.news.StoryGroup

/** The briefing over [sources]' current headlines: each region's biggest unread stories (see [Briefing]). */
fun NewsFeedState.briefing(sources: List<NewsSource>, isRead: (FeedItem) -> Boolean): List<StoryGroup<FeedItem>> {
    val byId = sources.associateBy { it.id }
    val headlines = headlines(byId.keys) { id -> byId[id]?.region?.ordinal ?: 0 }
    return Briefing.select(headlines, regionOf = { byId[it]?.region }, isRead = isRead)
}
