package io.github.fianna76.speedread.testing

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.fianna76.speedread.data.settings.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import java.io.File

/** A [UserPreferencesRepository] backed by a fresh DataStore file in [directory]. */
fun testPreferences(directory: File, scope: CoroutineScope): UserPreferencesRepository =
    UserPreferencesRepository(
        PreferenceDataStoreFactory.create(scope = scope) { File(directory, "settings.preferences_pb") },
    )
