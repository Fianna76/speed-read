package io.github.fianna76.speedread.ui.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.fianna76.speedread.SpeedReadApplication
import io.github.fianna76.speedread.data.news.ArticleError
import io.github.fianna76.speedread.data.news.ArticleRepository
import io.github.fianna76.speedread.data.news.ArticleResult
import io.github.fianna76.speedread.data.news.NewsRepository
import io.github.fianna76.speedread.data.settings.UserPreferencesRepository
import io.github.fianna76.speedread.news.FeedItem
import io.github.fianna76.speedread.news.NewsRegion
import io.github.fianna76.speedread.news.NewsSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A headline as shown in the Newsstand. */
data class NewsArticle(
    val item: FeedItem,
    val source: NewsSource,
    /** Reading progress if the article was opened before, else `null`. */
    val progress: Float?,
)

data class NewsstandUiState(
    /** False until cached or fresh headlines are available (or the first refresh ends). */
    val hasLoaded: Boolean = false,
    /** Sources the user has switched on, in catalogue order (Ireland, Europe, world). */
    val sources: List<NewsSource> = emptyList(),
    /** Region the list is filtered to; `null` shows every region. */
    val selectedRegion: NewsRegion? = null,
    /** Source the list is filtered to; `null` shows every source (of [selectedRegion]). */
    val selectedSourceId: String? = null,
    val articles: List<NewsArticle> = emptyList(),
    /** Selected sources whose last refresh failed. */
    val failedSources: List<NewsSource> = emptyList(),
    val isRefreshing: Boolean = false,
    val lastUpdated: Long? = null,
    /** Canonical URL of the article being downloaded for the reader, if any. */
    val preparingUrl: String? = null,
    /** Saved article to open in the reader; the UI navigates and calls [NewsstandViewModel.onReaderOpened]. */
    val bookToOpen: Long? = null,
    val message: NewsMessage? = null,
) {
    /** Regions that have at least one enabled source, in display order. */
    val regions: List<NewsRegion> get() = NewsRegion.entries.filter { region -> sources.any { it.region == region } }

    /** Sources offered as filters: those of the selected region, or all. */
    val sourcesInRegion: List<NewsSource> get() = sources.filter { selectedRegion == null || it.region == selectedRegion }
}

/** Feedback for an article that could not be prepared for the reader. */
data class NewsMessage(val id: Long, val item: FeedItem, val error: ArticleError)

class NewsstandViewModel(
    private val news: NewsRepository,
    private val articles: ArticleRepository,
    private val preferences: UserPreferencesRepository,
    externalScope: CoroutineScope,
) : ViewModel() {

    private val filter = MutableStateFlow(Filter())
    private val opening = MutableStateFlow(OpeningState())
    private var nextMessageId = 0L

    private val enabledSources = preferences.preferences.map { it.enabledNewsSources }.distinctUntilChanged()

    val uiState: StateFlow<NewsstandUiState> = combine(
        enabledSources,
        news.state,
        articles.observeProgress(),
        filter,
        opening,
    ) { sources, feedState, progress, chosen, openingState ->
        val region = chosen.region?.takeIf { r -> sources.any { it.region == r } }
        val selected = chosen.sourceId?.takeIf { id -> sources.any { it.id == id && (region == null || it.region == region) } }
        val shownSources = sources.filter { (region == null || it.region == region) && (selected == null || it.id == selected) }
        val byId = sources.associateBy { it.id }
        val shownIds = shownSources.map { it.id }
        NewsstandUiState(
            hasLoaded = feedState.feeds.keys.any { it in shownIds } || (!feedState.isRefreshing && openingState.refreshedOnce),
            sources = sources,
            selectedRegion = region,
            selectedSourceId = selected,
            articles = feedState.headlines(shownIds) { id -> byId[id]?.region?.ordinal ?: 0 }.mapNotNull { item ->
                byId[item.sourceId]?.let { source -> NewsArticle(item, source, progress[item.canonicalUrl]) }
            },
            failedSources = shownSources.filter { it.id in feedState.failedSources },
            isRefreshing = feedState.isRefreshing,
            lastUpdated = feedState.lastUpdated(shownIds),
            preparingUrl = openingState.preparingUrl,
            bookToOpen = openingState.bookToOpen,
            message = openingState.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NewsstandUiState())

    init {
        viewModelScope.launch {
            // Fetch on open, and whenever a source is switched on, unless headlines are recent.
            enabledSources.collect { sources ->
                news.refreshIfStale(sources)
                opening.update { it.copy(refreshedOnce = true) }
            }
        }
        externalScope.launch { articles.pruneOld() }
    }

    /** Shows one region's stories (`null` for all); clears a source filter from another region. */
    fun selectRegion(region: NewsRegion?) = filter.update { Filter(region = region) }

    fun selectSource(sourceId: String?) = filter.update { it.copy(sourceId = sourceId) }

    /** Refreshes the sources currently shown. */
    fun refresh() {
        viewModelScope.launch {
            val shown = uiState.value.let { state ->
                state.sources.filter { source ->
                    (state.selectedRegion == null || source.region == state.selectedRegion) &&
                        (state.selectedSourceId == null || source.id == state.selectedSourceId)
                }
            }
            news.refresh(shown.ifEmpty { enabledSources.first() })
        }
    }

    /** Downloads the story text (unless saved already), then asks the UI to open the reader. */
    fun readInApp(item: FeedItem) {
        if (opening.value.preparingUrl != null) return
        opening.update { it.copy(preparingUrl = item.canonicalUrl) }
        viewModelScope.launch {
            val result = try {
                articles.open(item)
            } finally {
                opening.update { it.copy(preparingUrl = null) }
            }
            when (result) {
                is ArticleResult.Ready -> opening.update { it.copy(bookToOpen = result.bookId) }
                is ArticleResult.Failed -> opening.update { it.copy(message = NewsMessage(nextMessageId++, item, result.reason)) }
            }
        }
    }

    fun onReaderOpened() = opening.update { it.copy(bookToOpen = null) }

    fun onMessageShown(id: Long) = opening.update { state ->
        if (state.message?.id == id) state.copy(message = null) else state
    }

    private data class Filter(val region: NewsRegion? = null, val sourceId: String? = null)

    private data class OpeningState(
        val preparingUrl: String? = null,
        val bookToOpen: Long? = null,
        val message: NewsMessage? = null,
        val refreshedOnce: Boolean = false,
    )

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as SpeedReadApplication).container
                NewsstandViewModel(
                    container.newsRepository,
                    container.articleRepository,
                    container.preferencesRepository,
                    container.applicationScope,
                )
            }
        }
    }
}
