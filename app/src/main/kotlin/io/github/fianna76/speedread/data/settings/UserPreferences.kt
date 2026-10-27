package io.github.fianna76.speedread.data.settings

import io.github.fianna76.speedread.news.Briefing
import io.github.fianna76.speedread.news.NewsRegion
import io.github.fianna76.speedread.news.NewsSource
import io.github.fianna76.speedread.news.NewsSources
import io.github.fianna76.speedread.rsvp.PlaybackSettings

/** Everything the user can configure, with defaults for a fresh install. */
data class UserPreferences(
    val playback: PlaybackSettings = PlaybackSettings(),
    val display: ReaderDisplay = ReaderDisplay(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val librarySort: LibrarySort = LibrarySort.RECENT,
    /** Raise the speed by [SPEED_TRAINING_STEP] every [SPEED_TRAINING_INTERVAL_MINUTES] of reading, up to [trainingTargetWpm]. */
    val speedTraining: Boolean = false,
    val trainingTargetWpm: Int = DEFAULT_TRAINING_TARGET,
    /** In the reader, volume up/down move to the next/previous sentence. */
    val volumeKeysTurnSentences: Boolean = false,
    /** Hide stories already opened from the Newsstand list. */
    val hideReadNews: Boolean = false,
    /** When news thumbnails are loaded (data saver). */
    val newsImages: NewsImages = NewsImages.ALWAYS,
    /** After finishing a news article, start the next story automatically (after a short countdown). */
    val autoContinueNews: Boolean = true,
    /** Refresh headlines and download saved and top stories in the background, on unmetered networks. */
    val backgroundNews: Boolean = false,
    /** Notify when Irish outlets lead with the same breaking story. */
    val breakingNewsAlerts: Boolean = false,
    /** A daily notification with the morning briefing, downloaded and ready to read. */
    val morningBriefing: Boolean = false,
    /** When the morning briefing arrives, in minutes after midnight (local time). */
    val briefingTime: Int = DEFAULT_BRIEFING_TIME,
    /** Stories the briefing takes from each region (1 to 3). */
    val briefingPerRegion: Int = Briefing.PER_REGION,
    /** Regions the briefing covers. */
    val briefingRegions: Set<NewsRegion> = NewsRegion.entries.toSet(),
    /** Send the briefing notification on Saturdays and Sundays too. */
    val briefingWeekends: Boolean = true,
    /** Regions whose outlets can raise breaking-news alerts. */
    val alertRegions: Set<NewsRegion> = setOf(NewsRegion.IRELAND),
    /** No alerts from [QUIET_START] to [QUIET_END] (local time). */
    val alertQuietHours: Boolean = true,
    /** Most alerts per day. */
    val alertDailyLimit: Int = DEFAULT_ALERT_LIMIT,
    /** Ids of [NewsSources] the user switched off. */
    val disabledNewsSources: Set<String> = emptySet(),
    /** Ids of [NewsSources] the user switched on (sources not enabled by default). */
    val addedNewsSources: Set<String> = emptySet(),
    /** NBA teams the Sports section follows (abbreviations, see `NbaTeams`). */
    val followedNbaTeams: Set<String> = emptySet(),
    /** Motorsport series the Sports section follows (ids, see `MotorsportCatalog`). */
    val followedSeries: Set<String> = emptySet(),
    /** Feeds the user added themselves, in the order added. */
    val customNewsSources: List<NewsSource> = emptyList(),
) {
    /** The user's choice if they made one, else the source's default. */
    fun isNewsSourceEnabled(source: NewsSource): Boolean = when (source.id) {
        in addedNewsSources -> true
        in disabledNewsSources -> false
        else -> source.enabledByDefault
    }

    /** Enabled sources, built-in and the user's own, grouped by region (Ireland, Europe, world). */
    val enabledNewsSources: List<NewsSource>
        get() = (NewsSources.ALL + customNewsSources).filter(::isNewsSourceEnabled).sortedBy { it.region }

    /** A source by id, including the user's own feeds. */
    fun newsSource(id: String): NewsSource? = NewsSources.byId(id) ?: customNewsSources.firstOrNull { it.id == id }

    companion object {
        const val DEFAULT_BRIEFING_TIME = 7 * 60

        const val DEFAULT_ALERT_LIMIT = 5
        val ALERT_LIMITS: List<Int> = listOf(1, 3, 5, 10)
        /** Quiet hours, in minutes after midnight. */
        const val QUIET_START = 22 * 60
        const val QUIET_END = 7 * 60

        const val DEFAULT_TRAINING_TARGET = 500
        const val SPEED_TRAINING_STEP = 25
        const val SPEED_TRAINING_INTERVAL_MINUTES = 2

        /** Training targets offered in Settings. */
        val TRAINING_TARGETS: List<Int> = listOf(400, 500, 600, 800, 1000)

        /** Briefing times offered in Settings: every half hour from 5:30 to 10:00. */
        val BRIEFING_TIMES: List<Int> = (11..20).map { it * 30 }
    }
}

/** How words are rendered on the reading screen. */
data class ReaderDisplay(
    val fontSizeSp: Int = DEFAULT_FONT_SIZE_SP,
    val font: ReaderFont = ReaderFont.SANS,
    /** Colour the optimal recognition point so the eye knows where to rest. */
    val highlightFocusLetter: Boolean = true,
    /** Show the surrounding sentence while paused. */
    val showContextWhenPaused: Boolean = true,
) {
    companion object {
        const val MIN_FONT_SIZE_SP = 24
        const val MAX_FONT_SIZE_SP = 72
        const val DEFAULT_FONT_SIZE_SP = 40
    }
}

enum class ReaderFont { SANS, SERIF, MONOSPACE }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class LibrarySort { RECENT, TITLE, AUTHOR, ADDED }

/** Data saver for news thumbnails. */
enum class NewsImages { ALWAYS, UNMETERED_ONLY, NEVER }
