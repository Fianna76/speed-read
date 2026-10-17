package io.github.fianna76.speedread.data.settings

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.fianna76.speedread.rsvp.PlaybackSettings
import io.github.fianna76.speedread.rsvp.WordsPerMinute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Persists [UserPreferences] with Jetpack DataStore. */
class UserPreferencesRepository(private val dataStore: DataStore<Preferences>) {

    val preferences: Flow<UserPreferences> = dataStore.data
        .catch { e ->
            // A corrupt or unreadable file shouldn't brick the app: fall back to defaults.
            if (e is IOException) {
                Log.w(TAG, "Could not read preferences", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { it.toUserPreferences() }
        .distinctUntilChanged()

    suspend fun setWordsPerMinute(wpm: Int) = update { it[Keys.WPM] = WordsPerMinute.clamp(wpm) }

    suspend fun setSmartPacing(enabled: Boolean) = update { it[Keys.SMART_PACING] = enabled }

    suspend fun setRampUp(enabled: Boolean) = update { it[Keys.RAMP_UP] = enabled }

    suspend fun setFontSize(sizeSp: Int) = update {
        it[Keys.FONT_SIZE] = sizeSp.coerceIn(ReaderDisplay.MIN_FONT_SIZE_SP, ReaderDisplay.MAX_FONT_SIZE_SP)
    }

    suspend fun setFont(font: ReaderFont) = update { it[Keys.FONT] = font.name }

    suspend fun setHighlightFocusLetter(enabled: Boolean) = update { it[Keys.HIGHLIGHT_FOCUS] = enabled }

    suspend fun setShowContextWhenPaused(enabled: Boolean) = update { it[Keys.SHOW_CONTEXT] = enabled }

    suspend fun setThemeMode(mode: ThemeMode) = update { it[Keys.THEME] = mode.name }

    suspend fun setLibrarySort(sort: LibrarySort) = update { it[Keys.LIBRARY_SORT] = sort.name }

    suspend fun setHideReadNews(hide: Boolean) = update { it[Keys.HIDE_READ_NEWS] = hide }

    suspend fun setNewsImages(images: NewsImages) = update { it[Keys.NEWS_IMAGES] = images.name }

    suspend fun setAutoContinueNews(enabled: Boolean) = update { it[Keys.AUTO_CONTINUE_NEWS] = enabled }

    suspend fun setBackgroundNews(enabled: Boolean) = update { it[Keys.BACKGROUND_NEWS] = enabled }

    suspend fun setBreakingNewsAlerts(enabled: Boolean) = update { it[Keys.BREAKING_NEWS_ALERTS] = enabled }

    suspend fun setMorningBriefing(enabled: Boolean) = update { it[Keys.MORNING_BRIEFING] = enabled }

    suspend fun setBriefingTime(minuteOfDay: Int) = update { it[Keys.BRIEFING_TIME] = minuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1) }

    /** Switches several sources at once (e.g. a whole region). */
    suspend fun setNewsSourcesEnabled(sourceIds: Collection<String>, enabled: Boolean) = update {
        val disabled = it[Keys.DISABLED_NEWS_SOURCES].orEmpty()
        val added = it[Keys.ADDED_NEWS_SOURCES].orEmpty()
        it[Keys.DISABLED_NEWS_SOURCES] = if (enabled) disabled - sourceIds.toSet() else disabled + sourceIds
        it[Keys.ADDED_NEWS_SOURCES] = if (enabled) added + sourceIds else added - sourceIds.toSet()
    }

    suspend fun setNewsSourceEnabled(sourceId: String, enabled: Boolean) = setNewsSourcesEnabled(listOf(sourceId), enabled)

    private suspend fun update(block: (MutablePreferences) -> Unit) {
        dataStore.edit { block(it) }
    }

    private fun Preferences.toUserPreferences(): UserPreferences {
        val defaults = UserPreferences()
        return UserPreferences(
            playback = PlaybackSettings(
                wordsPerMinute = WordsPerMinute.clamp(this[Keys.WPM] ?: defaults.playback.wordsPerMinute),
                smartPacing = this[Keys.SMART_PACING] ?: defaults.playback.smartPacing,
                rampUp = this[Keys.RAMP_UP] ?: defaults.playback.rampUp,
            ),
            display = ReaderDisplay(
                fontSizeSp = (this[Keys.FONT_SIZE] ?: defaults.display.fontSizeSp)
                    .coerceIn(ReaderDisplay.MIN_FONT_SIZE_SP, ReaderDisplay.MAX_FONT_SIZE_SP),
                font = enumOrDefault(this[Keys.FONT], defaults.display.font),
                highlightFocusLetter = this[Keys.HIGHLIGHT_FOCUS] ?: defaults.display.highlightFocusLetter,
                showContextWhenPaused = this[Keys.SHOW_CONTEXT] ?: defaults.display.showContextWhenPaused,
            ),
            themeMode = enumOrDefault(this[Keys.THEME], defaults.themeMode),
            librarySort = enumOrDefault(this[Keys.LIBRARY_SORT], defaults.librarySort),
            hideReadNews = this[Keys.HIDE_READ_NEWS] ?: defaults.hideReadNews,
            newsImages = enumOrDefault(this[Keys.NEWS_IMAGES], defaults.newsImages),
            autoContinueNews = this[Keys.AUTO_CONTINUE_NEWS] ?: defaults.autoContinueNews,
            backgroundNews = this[Keys.BACKGROUND_NEWS] ?: defaults.backgroundNews,
            breakingNewsAlerts = this[Keys.BREAKING_NEWS_ALERTS] ?: defaults.breakingNewsAlerts,
            morningBriefing = this[Keys.MORNING_BRIEFING] ?: defaults.morningBriefing,
            briefingTime = (this[Keys.BRIEFING_TIME] ?: defaults.briefingTime).coerceIn(0, MINUTES_PER_DAY - 1),
            disabledNewsSources = this[Keys.DISABLED_NEWS_SOURCES] ?: defaults.disabledNewsSources,
            addedNewsSources = this[Keys.ADDED_NEWS_SOURCES] ?: defaults.addedNewsSources,
        )
    }

    private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
        name?.let { stored -> enumValues<E>().firstOrNull { it.name == stored } } ?: default

    private object Keys {
        val WPM = intPreferencesKey("words_per_minute")
        val SMART_PACING = booleanPreferencesKey("smart_pacing")
        val RAMP_UP = booleanPreferencesKey("ramp_up")
        val FONT_SIZE = intPreferencesKey("font_size_sp")
        val FONT = stringPreferencesKey("reader_font")
        val HIGHLIGHT_FOCUS = booleanPreferencesKey("highlight_focus_letter")
        val SHOW_CONTEXT = booleanPreferencesKey("show_context_when_paused")
        val THEME = stringPreferencesKey("theme_mode")
        val LIBRARY_SORT = stringPreferencesKey("library_sort")
        val HIDE_READ_NEWS = booleanPreferencesKey("hide_read_news")
        val NEWS_IMAGES = stringPreferencesKey("news_images")
        val AUTO_CONTINUE_NEWS = booleanPreferencesKey("auto_continue_news")
        val BACKGROUND_NEWS = booleanPreferencesKey("background_news")
        val BREAKING_NEWS_ALERTS = booleanPreferencesKey("breaking_news_alerts")
        val MORNING_BRIEFING = booleanPreferencesKey("morning_briefing")
        val BRIEFING_TIME = intPreferencesKey("briefing_time")
        val DISABLED_NEWS_SOURCES = stringSetPreferencesKey("disabled_news_sources")
        val ADDED_NEWS_SOURCES = stringSetPreferencesKey("added_news_sources")
    }

    private companion object {
        const val TAG = "UserPreferences"
        const val MINUTES_PER_DAY = 24 * 60
    }
}
