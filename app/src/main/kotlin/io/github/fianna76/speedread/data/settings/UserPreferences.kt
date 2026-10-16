package io.github.fianna76.speedread.data.settings

import io.github.fianna76.speedread.rsvp.PlaybackSettings

/** Everything the user can configure, with defaults for a fresh install. */
data class UserPreferences(
    val playback: PlaybackSettings = PlaybackSettings(),
    val display: ReaderDisplay = ReaderDisplay(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val librarySort: LibrarySort = LibrarySort.RECENT,
)

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
