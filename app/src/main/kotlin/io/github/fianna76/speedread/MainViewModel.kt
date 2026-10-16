package io.github.fianna76.speedread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.fianna76.speedread.data.settings.ThemeMode
import io.github.fianna76.speedread.data.settings.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Activity-level state: the theme, which must be known before the first frame is drawn. */
class MainViewModel(preferences: UserPreferencesRepository) : ViewModel() {

    /** `null` until preferences are loaded; the splash screen stays up until then. */
    val themeMode: StateFlow<ThemeMode?> = preferences.preferences
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MainViewModel((this[APPLICATION_KEY] as SpeedReadApplication).container.preferencesRepository)
            }
        }
    }
}
