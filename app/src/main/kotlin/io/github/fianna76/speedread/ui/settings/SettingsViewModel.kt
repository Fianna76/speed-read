package io.github.fianna76.speedread.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.fianna76.speedread.SpeedReadApplication
import io.github.fianna76.speedread.data.settings.NewsImages
import io.github.fianna76.speedread.data.settings.ReaderFont
import io.github.fianna76.speedread.data.settings.ThemeMode
import io.github.fianna76.speedread.data.settings.UserPreferences
import io.github.fianna76.speedread.data.settings.UserPreferencesRepository
import io.github.fianna76.speedread.data.news.CustomFeedResolver
import io.github.fianna76.speedread.news.NewsRegion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Progress of "Add a feed". */
data class AddFeedState(
    val working: Boolean = false,
    val error: AddFeedError? = null,
    /** Name of the feed just added (or the built-in source switched on); the dialog closes. */
    val added: String? = null,
    val wasBuiltIn: Boolean = false,
)

enum class AddFeedError { INVALID, NETWORK, NO_FEED }

class SettingsViewModel(
    private val preferences: UserPreferencesRepository,
    private val feeds: CustomFeedResolver? = null,
) : ViewModel() {

    private val _addFeed = MutableStateFlow(AddFeedState())
    val addFeed: StateFlow<AddFeedState> = _addFeed.asStateFlow()

    /** Looks for the feed at [input] (a feed or a site address) and adds it, switched on. */
    fun addFeed(input: String, region: NewsRegion) {
        val resolver = feeds ?: return
        if (_addFeed.value.working) return
        _addFeed.value = AddFeedState(working = true)
        viewModelScope.launch {
            _addFeed.value = when (val result = resolver.resolve(input, region)) {
                is CustomFeedResolver.Result.Found -> {
                    preferences.addCustomNewsSource(result.source)
                    AddFeedState(added = result.source.name)
                }
                is CustomFeedResolver.Result.BuiltIn -> {
                    preferences.setNewsSourceEnabled(result.source.id, true)
                    AddFeedState(added = result.source.name, wasBuiltIn = true)
                }
                CustomFeedResolver.Result.Invalid -> AddFeedState(error = AddFeedError.INVALID)
                CustomFeedResolver.Result.Network -> AddFeedState(error = AddFeedError.NETWORK)
                CustomFeedResolver.Result.NoFeed -> AddFeedState(error = AddFeedError.NO_FEED)
            }
        }
    }

    fun resetAddFeed() {
        if (!_addFeed.value.working) _addFeed.value = AddFeedState()
    }

    fun removeFeed(sourceId: String) = launch { preferences.removeCustomNewsSource(sourceId) }

    /** `null` until the stored preferences have been read. */
    val uiState: StateFlow<UserPreferences?> = preferences.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setWordsPerMinute(wpm: Int) = launch { preferences.setWordsPerMinute(wpm) }

    fun setSmartPacing(enabled: Boolean) = launch { preferences.setSmartPacing(enabled) }

    fun setRampUp(enabled: Boolean) = launch { preferences.setRampUp(enabled) }

    fun setChunkSize(words: Int) = launch { preferences.setChunkSize(words) }

    fun setSpeedTraining(enabled: Boolean) = launch { preferences.setSpeedTraining(enabled) }

    fun setTrainingTarget(wpm: Int) = launch { preferences.setTrainingTarget(wpm) }

    fun setFontSize(sizeSp: Int) = launch { preferences.setFontSize(sizeSp) }

    fun setFont(font: ReaderFont) = launch { preferences.setFont(font) }

    fun setHighlightFocusLetter(enabled: Boolean) = launch { preferences.setHighlightFocusLetter(enabled) }

    fun setShowContextWhenPaused(enabled: Boolean) = launch { preferences.setShowContextWhenPaused(enabled) }

    fun setVolumeKeysTurnSentences(enabled: Boolean) = launch { preferences.setVolumeKeysTurnSentences(enabled) }

    fun setThemeMode(mode: ThemeMode) = launch { preferences.setThemeMode(mode) }

    fun setNewsSourceEnabled(sourceId: String, enabled: Boolean) = launch { preferences.setNewsSourceEnabled(sourceId, enabled) }

    fun setNewsSourcesEnabled(sourceIds: Collection<String>, enabled: Boolean) =
        launch { preferences.setNewsSourcesEnabled(sourceIds, enabled) }

    fun setNewsImages(images: NewsImages) = launch { preferences.setNewsImages(images) }

    fun setAutoContinueNews(enabled: Boolean) = launch { preferences.setAutoContinueNews(enabled) }

    fun setBackgroundNews(enabled: Boolean) = launch { preferences.setBackgroundNews(enabled) }

    fun setBreakingNewsAlerts(enabled: Boolean) = launch { preferences.setBreakingNewsAlerts(enabled) }

    fun setMorningBriefing(enabled: Boolean) = launch { preferences.setMorningBriefing(enabled) }

    fun setBriefingPerRegion(stories: Int) = launch { preferences.setBriefingPerRegion(stories) }

    fun setBriefingRegion(region: NewsRegion, included: Boolean) = launch { preferences.setBriefingRegion(region, included) }

    fun setBriefingWeekends(enabled: Boolean) = launch { preferences.setBriefingWeekends(enabled) }

    fun setAlertRegion(region: NewsRegion, included: Boolean) = launch { preferences.setAlertRegion(region, included) }

    fun setAlertQuietHours(enabled: Boolean) = launch { preferences.setAlertQuietHours(enabled) }

    fun setAlertDailyLimit(limit: Int) = launch { preferences.setAlertDailyLimit(limit) }

    fun setBriefingTime(minuteOfDay: Int) = launch { preferences.setBriefingTime(minuteOfDay) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as SpeedReadApplication).container
                SettingsViewModel(container.preferencesRepository, container.customFeedResolver)
            }
        }
    }
}
