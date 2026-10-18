package io.github.fianna76.speedread.data.settings

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
    /** Ids of [NewsSources] the user switched off. */
    val disabledNewsSources: Set<String> = emptySet(),
    /** Ids of [NewsSources] the user switched on (sources not enabled by default). */
    val addedNewsSources: Set<String> = emptySet(),
) {
    /** The user's choice if they made one, else the source's default. */
    fun isNewsSourceEnabled(source: NewsSource): Boolean = when (source.id) {
        in addedNewsSources -> true
        in disabledNewsSources -> false
        else -> source.enabledByDefault
    }

    val enabledNewsSources: List<NewsSource> get() = NewsSources.ALL.filter(::isNewsSourceEnabled)

    companion object {
        const val DEFAULT_BRIEFING_TIME = 7 * 60

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
