package io.github.fianna76.studypack.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import io.github.fianna76.speedread.rsvp.WordsPerMinute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** The speed-reading tool's settings. */
data class ReadingSettings(
    val wordsPerMinute: Int = WordsPerMinute.DEFAULT,
)

class ReadingPreferences(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<ReadingSettings> = dataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { prefs -> ReadingSettings(wordsPerMinute = WordsPerMinute.clamp(prefs[WPM] ?: WordsPerMinute.DEFAULT)) }

    suspend fun setWordsPerMinute(wpm: Int) {
        dataStore.edit { it[WPM] = WordsPerMinute.clamp(wpm) }
    }

    private companion object {
        val WPM = intPreferencesKey("rsvp_wpm")
    }
}
