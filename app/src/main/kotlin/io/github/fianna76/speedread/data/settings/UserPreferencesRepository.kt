package io.github.fianna76.speedread.data.settings

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.fianna76.speedread.news.NewsRegion
import io.github.fianna76.speedread.news.NewsSource
import io.github.fianna76.speedread.rsvp.Chunking
import io.github.fianna76.speedread.rsvp.PlaybackSettings
import io.github.fianna76.speedread.rsvp.WordsPerMinute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
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

    suspend fun setChunkSize(words: Int) = update { it[Keys.CHUNK_SIZE] = words.coerceIn(1, Chunking.MAX_SIZE) }

    suspend fun setSpeedTraining(enabled: Boolean) = update { it[Keys.SPEED_TRAINING] = enabled }

    suspend fun setTrainingTarget(wpm: Int) = update { it[Keys.TRAINING_TARGET] = WordsPerMinute.clamp(wpm) }

    suspend fun setFontSize(sizeSp: Int) = update {
        it[Keys.FONT_SIZE] = sizeSp.coerceIn(ReaderDisplay.MIN_FONT_SIZE_SP, ReaderDisplay.MAX_FONT_SIZE_SP)
    }

    suspend fun setFont(font: ReaderFont) = update { it[Keys.FONT] = font.name }

    suspend fun setHighlightFocusLetter(enabled: Boolean) = update { it[Keys.HIGHLIGHT_FOCUS] = enabled }

    suspend fun setShowContextWhenPaused(enabled: Boolean) = update { it[Keys.SHOW_CONTEXT] = enabled }

    suspend fun setThemeMode(mode: ThemeMode) = update { it[Keys.THEME] = mode.name }

    suspend fun setLibrarySort(sort: LibrarySort) = update { it[Keys.LIBRARY_SORT] = sort.name }

    suspend fun setVolumeKeysTurnSentences(enabled: Boolean) = update { it[Keys.VOLUME_KEYS] = enabled }

    suspend fun setHideReadNews(hide: Boolean) = update { it[Keys.HIDE_READ_NEWS] = hide }

    suspend fun setNewsImages(images: NewsImages) = update { it[Keys.NEWS_IMAGES] = images.name }

    suspend fun setAutoContinueNews(enabled: Boolean) = update { it[Keys.AUTO_CONTINUE_NEWS] = enabled }

    suspend fun setBackgroundNews(enabled: Boolean) = update { it[Keys.BACKGROUND_NEWS] = enabled }

    suspend fun setBreakingNewsAlerts(enabled: Boolean) = update { it[Keys.BREAKING_NEWS_ALERTS] = enabled }

    suspend fun setMorningBriefing(enabled: Boolean) = update { it[Keys.MORNING_BRIEFING] = enabled }

    suspend fun setBriefingPerRegion(stories: Int) = update { it[Keys.BRIEFING_PER_REGION] = stories.coerceIn(1, MAX_PER_REGION) }

    suspend fun setBriefingRegion(region: NewsRegion, included: Boolean) = update {
        val current = regionsOrDefault(it[Keys.BRIEFING_REGIONS], NewsRegion.entries.toSet())
        it[Keys.BRIEFING_REGIONS] = (if (included) current + region else current - region).mapTo(HashSet()) { r -> r.name }
    }

    suspend fun setBriefingWeekends(enabled: Boolean) = update { it[Keys.BRIEFING_WEEKENDS] = enabled }

    suspend fun setAlertRegion(region: NewsRegion, included: Boolean) = update {
        val current = regionsOrDefault(it[Keys.ALERT_REGIONS], setOf(NewsRegion.IRELAND))
        it[Keys.ALERT_REGIONS] = (if (included) current + region else current - region).mapTo(HashSet()) { r -> r.name }
    }

    suspend fun setAlertQuietHours(enabled: Boolean) = update { it[Keys.ALERT_QUIET_HOURS] = enabled }

    suspend fun setAlertDailyLimit(limit: Int) = update { it[Keys.ALERT_DAILY_LIMIT] = limit.coerceAtLeast(1) }

    suspend fun setBriefingTime(minuteOfDay: Int) = update { it[Keys.BRIEFING_TIME] = minuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1) }

    /** Switches several sources at once (e.g. a whole region). */
    suspend fun setNewsSourcesEnabled(sourceIds: Collection<String>, enabled: Boolean) = update {
        val disabled = it[Keys.DISABLED_NEWS_SOURCES].orEmpty()
        val added = it[Keys.ADDED_NEWS_SOURCES].orEmpty()
        it[Keys.DISABLED_NEWS_SOURCES] = if (enabled) disabled - sourceIds.toSet() else disabled + sourceIds
        it[Keys.ADDED_NEWS_SOURCES] = if (enabled) added + sourceIds else added - sourceIds.toSet()
    }

    suspend fun setNewsSourceEnabled(sourceId: String, enabled: Boolean) = setNewsSourcesEnabled(listOf(sourceId), enabled)

    /** Adds (or replaces, by id) a feed the user found; it starts switched on. */
    suspend fun addCustomNewsSource(source: NewsSource) = update {
        val sources = decodeSources(it[Keys.CUSTOM_NEWS_SOURCES]).filterNot { old -> old.id == source.id } + source
        it[Keys.CUSTOM_NEWS_SOURCES] = encodeSources(sources)
        it[Keys.DISABLED_NEWS_SOURCES] = it[Keys.DISABLED_NEWS_SOURCES].orEmpty() - source.id
    }

    suspend fun removeCustomNewsSource(sourceId: String) = update {
        it[Keys.CUSTOM_NEWS_SOURCES] = encodeSources(decodeSources(it[Keys.CUSTOM_NEWS_SOURCES]).filterNot { old -> old.id == sourceId })
        it[Keys.DISABLED_NEWS_SOURCES] = it[Keys.DISABLED_NEWS_SOURCES].orEmpty() - sourceId
    }

    /** Every stored setting as JSON (`{"key": {"type": "int", "value": 300}}`), for backups. */
    suspend fun exportJson(): JsonObject = buildJsonObject {
        dataStore.data.first().asMap().forEach { (key, value) ->
            val (type, json) = when (value) {
                is Boolean -> "boolean" to JsonPrimitive(value)
                is Int -> "int" to JsonPrimitive(value)
                is Long -> "long" to JsonPrimitive(value)
                is Float -> "float" to JsonPrimitive(value)
                is String -> "string" to JsonPrimitive(value)
                is Set<*> -> "strings" to JsonArray(value.map { JsonPrimitive(it.toString()) })
                else -> return@forEach
            }
            put(key.name, buildJsonObject {
                put("type", type)
                put("value", json)
            })
        }
    }

    /** Replaces the settings with a backup's; unknown or malformed entries are skipped. */
    suspend fun importJson(json: JsonObject) = update { prefs ->
        prefs.clear()
        json.forEach { (name, entry) ->
            runCatching {
                val obj = entry.jsonObject
                val value = obj.getValue("value")
                when (obj.getValue("type").jsonPrimitive.content) {
                    "boolean" -> prefs[booleanPreferencesKey(name)] = value.jsonPrimitive.boolean
                    "int" -> prefs[intPreferencesKey(name)] = value.jsonPrimitive.int
                    "long" -> prefs[longPreferencesKey(name)] = value.jsonPrimitive.long
                    "float" -> prefs[floatPreferencesKey(name)] = value.jsonPrimitive.float
                    "string" -> prefs[stringPreferencesKey(name)] = value.jsonPrimitive.content
                    "strings" -> prefs[stringSetPreferencesKey(name)] = value.jsonArray.mapTo(HashSet()) { it.jsonPrimitive.content }
                }
            }.onFailure { Log.w(TAG, "Skipping setting $name from backup", it) }
        }
    }

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
                chunkSize = (this[Keys.CHUNK_SIZE] ?: defaults.playback.chunkSize).coerceIn(1, Chunking.MAX_SIZE),
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
            speedTraining = this[Keys.SPEED_TRAINING] ?: defaults.speedTraining,
            trainingTargetWpm = WordsPerMinute.clamp(this[Keys.TRAINING_TARGET] ?: defaults.trainingTargetWpm),
            volumeKeysTurnSentences = this[Keys.VOLUME_KEYS] ?: defaults.volumeKeysTurnSentences,
            hideReadNews = this[Keys.HIDE_READ_NEWS] ?: defaults.hideReadNews,
            newsImages = enumOrDefault(this[Keys.NEWS_IMAGES], defaults.newsImages),
            autoContinueNews = this[Keys.AUTO_CONTINUE_NEWS] ?: defaults.autoContinueNews,
            backgroundNews = this[Keys.BACKGROUND_NEWS] ?: defaults.backgroundNews,
            breakingNewsAlerts = this[Keys.BREAKING_NEWS_ALERTS] ?: defaults.breakingNewsAlerts,
            morningBriefing = this[Keys.MORNING_BRIEFING] ?: defaults.morningBriefing,
            briefingTime = (this[Keys.BRIEFING_TIME] ?: defaults.briefingTime).coerceIn(0, MINUTES_PER_DAY - 1),
            briefingPerRegion = (this[Keys.BRIEFING_PER_REGION] ?: defaults.briefingPerRegion).coerceIn(1, MAX_PER_REGION),
            briefingRegions = regionsOrDefault(this[Keys.BRIEFING_REGIONS], defaults.briefingRegions),
            briefingWeekends = this[Keys.BRIEFING_WEEKENDS] ?: defaults.briefingWeekends,
            alertRegions = regionsOrDefault(this[Keys.ALERT_REGIONS], defaults.alertRegions),
            alertQuietHours = this[Keys.ALERT_QUIET_HOURS] ?: defaults.alertQuietHours,
            alertDailyLimit = (this[Keys.ALERT_DAILY_LIMIT] ?: defaults.alertDailyLimit).coerceAtLeast(1),
            disabledNewsSources = this[Keys.DISABLED_NEWS_SOURCES] ?: defaults.disabledNewsSources,
            addedNewsSources = this[Keys.ADDED_NEWS_SOURCES] ?: defaults.addedNewsSources,
            customNewsSources = decodeSources(this[Keys.CUSTOM_NEWS_SOURCES]),
        )
    }

    private fun regionsOrDefault(names: Set<String>?, default: Set<NewsRegion>): Set<NewsRegion> =
        names?.mapNotNullTo(LinkedHashSet()) { name -> NewsRegion.entries.firstOrNull { it.name == name } } ?: default

    private fun encodeSources(sources: List<NewsSource>): String = JSON.encodeToString(
        sources.map { StoredSource(it.id, it.name, it.feedUrl, it.homepage, it.region.name) },
    )

    private fun decodeSources(json: String?): List<NewsSource> {
        if (json.isNullOrEmpty()) return emptyList()
        return runCatching { JSON.decodeFromString<List<StoredSource>>(json) }
            .onFailure { Log.w(TAG, "Discarding unreadable custom feeds", it) }
            .getOrDefault(emptyList())
            .map { stored ->
                NewsSource(
                    id = stored.id,
                    name = stored.name,
                    feedUrl = stored.feedUrl,
                    homepage = stored.homepage,
                    region = enumOrDefault(stored.region, NewsRegion.WORLD),
                    enabledByDefault = true,
                )
            }
    }

    @Serializable
    private data class StoredSource(val id: String, val name: String, val feedUrl: String, val homepage: String, val region: String)

    private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
        name?.let { stored -> enumValues<E>().firstOrNull { it.name == stored } } ?: default

    private object Keys {
        val WPM = intPreferencesKey("words_per_minute")
        val SMART_PACING = booleanPreferencesKey("smart_pacing")
        val RAMP_UP = booleanPreferencesKey("ramp_up")
        val CHUNK_SIZE = intPreferencesKey("chunk_size")
        val SPEED_TRAINING = booleanPreferencesKey("speed_training")
        val TRAINING_TARGET = intPreferencesKey("training_target_wpm")
        val FONT_SIZE = intPreferencesKey("font_size_sp")
        val FONT = stringPreferencesKey("reader_font")
        val HIGHLIGHT_FOCUS = booleanPreferencesKey("highlight_focus_letter")
        val SHOW_CONTEXT = booleanPreferencesKey("show_context_when_paused")
        val THEME = stringPreferencesKey("theme_mode")
        val LIBRARY_SORT = stringPreferencesKey("library_sort")
        val VOLUME_KEYS = booleanPreferencesKey("volume_keys_turn_sentences")
        val HIDE_READ_NEWS = booleanPreferencesKey("hide_read_news")
        val NEWS_IMAGES = stringPreferencesKey("news_images")
        val AUTO_CONTINUE_NEWS = booleanPreferencesKey("auto_continue_news")
        val BACKGROUND_NEWS = booleanPreferencesKey("background_news")
        val BREAKING_NEWS_ALERTS = booleanPreferencesKey("breaking_news_alerts")
        val MORNING_BRIEFING = booleanPreferencesKey("morning_briefing")
        val BRIEFING_TIME = intPreferencesKey("briefing_time")
        val BRIEFING_PER_REGION = intPreferencesKey("briefing_per_region")
        val BRIEFING_REGIONS = stringSetPreferencesKey("briefing_regions")
        val BRIEFING_WEEKENDS = booleanPreferencesKey("briefing_weekends")
        val ALERT_REGIONS = stringSetPreferencesKey("alert_regions")
        val ALERT_QUIET_HOURS = booleanPreferencesKey("alert_quiet_hours")
        val ALERT_DAILY_LIMIT = intPreferencesKey("alert_daily_limit")
        val DISABLED_NEWS_SOURCES = stringSetPreferencesKey("disabled_news_sources")
        val ADDED_NEWS_SOURCES = stringSetPreferencesKey("added_news_sources")
        val CUSTOM_NEWS_SOURCES = stringPreferencesKey("custom_news_sources")
    }

    private companion object {
        const val TAG = "UserPreferences"
        val JSON = Json { ignoreUnknownKeys = true }
        const val MINUTES_PER_DAY = 24 * 60
        const val MAX_PER_REGION = 3
    }
}
