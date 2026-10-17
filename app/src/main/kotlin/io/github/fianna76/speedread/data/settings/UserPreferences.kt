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
    /** Hide stories already opened from the Newsstand list. */
    val hideReadNews: Boolean = false,
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
