package io.github.fianna76.speedread.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.fianna76.speedread.SpeedReadApplication
import io.github.fianna76.speedread.data.settings.ReaderFont
import io.github.fianna76.speedread.data.settings.ThemeMode
import io.github.fianna76.speedread.data.settings.UserPreferences
import io.github.fianna76.speedread.data.settings.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val preferences: UserPreferencesRepository) : ViewModel() {

    /** `null` until the stored preferences have been read. */
    val uiState: StateFlow<UserPreferences?> = preferences.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setWordsPerMinute(wpm: Int) = launch { preferences.setWordsPerMinute(wpm) }

    fun setSmartPacing(enabled: Boolean) = launch { preferences.setSmartPacing(enabled) }

    fun setRampUp(enabled: Boolean) = launch { preferences.setRampUp(enabled) }

    fun setFontSize(sizeSp: Int) = launch { preferences.setFontSize(sizeSp) }

    fun setFont(font: ReaderFont) = launch { preferences.setFont(font) }

    fun setHighlightFocusLetter(enabled: Boolean) = launch { preferences.setHighlightFocusLetter(enabled) }

    fun setShowContextWhenPaused(enabled: Boolean) = launch { preferences.setShowContextWhenPaused(enabled) }

    fun setThemeMode(mode: ThemeMode) = launch { preferences.setThemeMode(mode) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel((this[APPLICATION_KEY] as SpeedReadApplication).container.preferencesRepository)
            }
        }
    }
}
