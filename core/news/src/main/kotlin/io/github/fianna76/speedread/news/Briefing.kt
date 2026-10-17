package io.github.fianna76.speedread.news

/**
 * Picks a short "briefing": the biggest unread stories of each region, home news first, to be
 * read back to back.
 *
 * Within a region, stories among its first [WINDOW] leads are ranked by how many outlets cover
 * them (then by feed rank), and the top [PER_REGION] are kept. Regions follow [NewsRegion]
 * order, so an Irish reader hears Irish news, then Europe, then the world.
 */
object Briefing {

    const val PER_REGION = 2
    const val WINDOW = 12

    /**
     * @param headlines enabled outlets' headlines in display (rank) order.
     * @param regionOf the region of a source id, or `null` if unknown.
     */
    fun select(
        headlines: List<FeedItem>,
        regionOf: (sourceId: String) -> NewsRegion?,
        isRead: (FeedItem) -> Boolean = { false },
        perRegion: Int = PER_REGION,
    ): List<StoryGroup<FeedItem>> {
        val groups = StoryGrouping.group(headlines, title = { it.title }, outlet = { it.sourceId })
            .filterNot { group -> isRead(group.lead) || group.related.any(isRead) }
        return NewsRegion.entries.flatMap { region ->
            groups.filter { regionOf(it.lead.sourceId) == region }
                .take(WINDOW)
                .sortedByDescending { it.related.size } // Stable: ties keep feed rank.
                .take(perRegion)
        }
    }
}
