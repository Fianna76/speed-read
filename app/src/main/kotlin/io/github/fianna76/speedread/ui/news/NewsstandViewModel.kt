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
import io.github.fianna76.speedread.data.news.NewsFeedState
import io.github.fianna76.speedread.data.news.NewsHistoryRepository
import io.github.fianna76.speedread.data.news.NewsQueue
import io.github.fianna76.speedread.data.news.NewsRepository
import io.github.fianna76.speedread.data.news.StoryHistory
import io.github.fianna76.speedread.data.news.briefing
import io.github.fianna76.speedread.data.settings.NewsImages
import io.github.fianna76.speedread.data.settings.UserPreferences
import io.github.fianna76.speedread.data.settings.UserPreferencesRepository
import io.github.fianna76.speedread.news.FeedItem
import io.github.fianna76.speedread.news.NewsRegion
import io.github.fianna76.speedread.news.NewsSource
import io.github.fianna76.speedread.news.NewsSources
import io.github.fianna76.speedread.news.NewsTopic
import io.github.fianna76.speedread.news.TopicClassifier
import io.github.fianna76.speedread.news.StoryGrouping
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/** A headline as shown in the Newsstand. */
data class NewsArticle(
    val item: FeedItem,
    val source: NewsSource,
    /** Reading progress if the article was downloaded for the reader, else `null`. */
    val progress: Float?,
    /** Opened before, in the reader or online. */
    val isRead: Boolean = false,
    /** On the reading list. */
    val isSaved: Boolean = false,
)

/** One story: the lead headline plus other outlets' versions of it. */
data class NewsStory(val article: NewsArticle, val related: List<NewsArticle> = emptyList())

data class NewsstandUiState(
    /** False until cached or fresh headlines are available (or the first refresh ends). */
    val hasLoaded: Boolean = false,
    /** Sources the user has switched on, in catalogue order (Ireland, Europe, world). */
    val sources: List<NewsSource> = emptyList(),
    /** Showing the reading list instead of the feed. */
    val showingSaved: Boolean = false,
    /** Region the feed is filtered to; `null` shows every region. */
    val selectedRegion: NewsRegion? = null,
    /** Source the feed is filtered to; `null` shows every source (of [selectedRegion]). */
    val selectedSourceId: String? = null,
    /** Topic the feed is filtered to; `null` shows every topic. */
    val selectedTopic: NewsTopic? = null,
    /** Stories per topic in the current region/outlet filter, for the topic menu. */
    val topicCounts: Map<NewsTopic, Int> = emptyMap(),
    /** Search text; empty when not searching. */
    val query: String = "",
    val hideRead: Boolean = false,
    /** Load thumbnails (the data saver may turn them off). */
    val showImages: Boolean = true,
    val stories: List<NewsStory> = emptyList(),
    /** Started but unfinished articles, most recently opened first. */
    val continueReading: List<NewsArticle> = emptyList(),
    val savedCount: Int = 0,
    /** Shown sources whose last refresh failed. */
    val failedSources: List<NewsSource> = emptyList(),
    val isRefreshing: Boolean = false,
    val lastUpdated: Long? = null,
    /** Canonical URL of the article being downloaded for the reader, if any. */
    val preparingUrl: String? = null,
    /** Saved article to open in the reader; the UI navigates and calls [NewsstandViewModel.onReaderOpened]. */
    val bookToOpen: Long? = null,
    /** Start [bookToOpen] playing right away (the briefing). */
    val autoPlayBook: Boolean = false,
    /** Each region's biggest unread stories, to read back to back (see [NewsstandViewModel.playBriefing]). */
    val briefing: List<NewsStory> = emptyList(),
    val message: NewsMessage? = null,
) {
    /** Regions that have at least one enabled source, in display order. */
    val regions: List<NewsRegion> get() = NewsRegion.entries.filter { region -> sources.any { it.region == region } }

    /** Sources offered as filters: those of the selected region, or all. */
    val sourcesInRegion: List<NewsSource> get() = sources.filter { selectedRegion == null || it.region == selectedRegion }

    val isSearching: Boolean get() = query.isNotBlank()

    /** The briefing is offered on the unfiltered feed only. */
    val showsBriefing: Boolean
        get() = briefing.isNotEmpty() && !showingSaved && !isSearching &&
            selectedRegion == null && selectedSourceId == null && selectedTopic == null

    /** Every headline on screen, leads and grouped versions alike. */
    val articles: List<NewsArticle> get() = stories.flatMap { listOf(it.article) + it.related }
}

/** One-off feedback, shown as a snackbar and acknowledged with [NewsstandViewModel.onMessageShown]. */
sealed interface NewsMessage {
    val id: Long

    /** An article could not be prepared for the reader. */
    data class ArticleFailed(override val id: Long, val item: FeedItem, val error: ArticleError) : NewsMessage

    /** Result of a manual refresh. */
    data class NewStories(override val id: Long, val count: Int) : NewsMessage

    /** A source was switched off from a card; the snackbar offers Undo. */
    data class SourceHidden(override val id: Long, val source: NewsSource) : NewsMessage
}

class NewsstandViewModel(
    private val news: NewsRepository,
    private val articles: ArticleRepository,
    private val history: NewsHistoryRepository,
    private val preferences: UserPreferencesRepository,
    private val externalScope: CoroutineScope,
    isMetered: Flow<Boolean> = flowOf(false),
    private val queue: NewsQueue = NewsQueue(),
    /** Called when the briefing starts playing (e.g. to clear its notification). */
    private val onBriefingStarted: () -> Unit = {},
) : ViewModel() {

    private val view = MutableStateFlow(ViewState())
    private val opening = MutableStateFlow(OpeningState())
    private var nextMessageId = 0L

    private val enabledSources = preferences.preferences.map { it.enabledNewsSources }.distinctUntilChanged()

    private val showImages = combine(preferences.preferences, isMetered) { prefs, metered ->
        when (prefs.newsImages) {
            NewsImages.ALWAYS -> true
            NewsImages.UNMETERED_ONLY -> !metered
            NewsImages.NEVER -> false
        }
    }.distinctUntilChanged()

    private val content = combine(
        preferences.preferences,
        news.state,
        articles.observeProgress(),
        history.observe(),
        view,
    ) { prefs, feedState, progress, entries, chosen -> buildContent(prefs, feedState, progress, entries, chosen) }
        .combine(showImages) { state, images -> state.copy(showImages = images) }
        .flowOn(Dispatchers.Default)

    val uiState: StateFlow<NewsstandUiState> = combine(content, opening) { state, openingState ->
        state.copy(
            hasLoaded = state.hasLoaded || openingState.refreshedOnce,
            preparingUrl = openingState.preparingUrl,
            bookToOpen = openingState.bookToOpen,
            autoPlayBook = openingState.autoPlay,
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
        externalScope.launch {
            articles.pruneOld()
            history.pruneOld()
        }
    }

    // --- Filters -------------------------------------------------------------------------------

    /** Shows one region's stories (`null` for all); leaves the reading list and clears a source filter. */
    fun selectRegion(region: NewsRegion?) = view.update { it.copy(saved = false, region = region, sourceId = null) }

    fun selectSource(sourceId: String?) = view.update { it.copy(saved = false, sourceId = sourceId) }

    fun selectTopic(topic: NewsTopic?) = view.update { it.copy(saved = false, topic = topic) }

    fun showSaved(show: Boolean) = view.update { it.copy(saved = show) }

    /** Clears every filter and search, e.g. to show a story tapped in the widget. */
    fun showAll() = view.update { ViewState() }

    fun setQuery(query: String) = view.update { it.copy(query = query) }

    fun setHideRead(hide: Boolean) {
        externalScope.launch { preferences.setHideReadNews(hide) }
    }

    // --- Refreshing ----------------------------------------------------------------------------

    /** Refreshes the sources currently shown and reports how many stories are new. */
    fun refresh() {
        viewModelScope.launch {
            val state = uiState.value
            val shown = state.sources.filter { source ->
                (state.selectedRegion == null || source.region == state.selectedRegion) &&
                    (state.selectedSourceId == null || source.id == state.selectedSourceId)
            }.ifEmpty { enabledSources.first() }
            val ids = shown.map { it.id }
            val before = news.state.value.headlines(ids).mapTo(HashSet()) { it.canonicalUrl }
            news.refresh(shown)
            val after = news.state.value.headlines(ids)
            // Only report when something was fetched (a failed refresh has its own message).
            if (after.isNotEmpty() && shown.any { it.id !in news.state.value.failedSources }) {
                postMessage { id -> NewsMessage.NewStories(id, after.count { it.canonicalUrl !in before }) }
            }
        }
    }

    // --- Story actions -------------------------------------------------------------------------

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
                is ArticleResult.Ready -> {
                    externalScope.launch { history.markRead(item) }
                    // "Next story" in the reader continues through the list this story came from.
                    val state = uiState.value
                    val list = (state.stories.map { it.article.item }).takeIf { list -> list.any { it.canonicalUrl == item.canonicalUrl } }
                        ?: (state.continueReading.map { it.item } + state.stories.map { it.article.item })
                    queue.set(list)
                    opening.update { it.copy(bookToOpen = result.bookId, autoPlay = false) }
                }
                is ArticleResult.Failed -> postMessage { id -> NewsMessage.ArticleFailed(id, item, result.reason) }
            }
        }
    }

    /**
     * Reads the briefing back to back: opens its first story that can be read (playing at
     * once), with the rest queued as "next story".
     */
    fun playBriefing() {
        if (opening.value.preparingUrl != null) return
        val items = uiState.value.briefing.map { it.article.item }
        if (items.isEmpty()) return
        onBriefingStarted()
        viewModelScope.launch {
            var firstFailure: ArticleResult.Failed? = null
            for (item in items) {
                opening.update { it.copy(preparingUrl = item.canonicalUrl) }
                val result = try {
                    articles.open(item)
                } finally {
                    opening.update { it.copy(preparingUrl = null) }
                }
                when (result) {
                    is ArticleResult.Ready -> {
                        externalScope.launch { history.markRead(item) }
                        queue.set(items)
                        opening.update { it.copy(bookToOpen = result.bookId, autoPlay = true) }
                        return@launch
                    }
                    // Subscriber-only or unreachable: the briefing goes on with the next story.
                    is ArticleResult.Failed -> if (firstFailure == null) firstFailure = result
                }
            }
            firstFailure?.let { failure -> postMessage { id -> NewsMessage.ArticleFailed(id, items.first(), failure.reason) } }
        }
    }

    /** The UI opened [item] in the browser. */
    fun onOpenedOnline(item: FeedItem) {
        externalScope.launch { history.markRead(item) }
    }

    fun setRead(item: FeedItem, read: Boolean) {
        externalScope.launch { history.setRead(item, read) }
    }

    /**
     * Adds [item] to the reading list (and downloads its text in the background, so it can be
     * speed-read offline), or removes it.
     */
    fun setSaved(item: FeedItem, saved: Boolean) {
        externalScope.launch {
            history.setSaved(item, saved)
            if (saved) articles.open(item) // Best effort; reading later retries if this fails.
        }
    }

    fun hideSource(source: NewsSource) {
        externalScope.launch { preferences.setNewsSourceEnabled(source.id, false) }
        view.update { if (it.sourceId == source.id) it.copy(sourceId = null) else it }
        postMessage { id -> NewsMessage.SourceHidden(id, source) }
    }

    fun undoHideSource(source: NewsSource) {
        externalScope.launch { preferences.setNewsSourceEnabled(source.id, true) }
    }

    fun onReaderOpened() = opening.update { it.copy(bookToOpen = null, autoPlay = false) }

    fun onMessageShown(id: Long) = opening.update { state ->
        if (state.message?.id == id) state.copy(message = null) else state
    }

    private fun postMessage(create: (Long) -> NewsMessage) = opening.update { it.copy(message = create(nextMessageId++)) }

    // --- Building the screen -------------------------------------------------------------------

    private fun buildContent(
        prefs: UserPreferences,
        feedState: NewsFeedState,
        progress: Map<String, Float>,
        entries: Map<String, StoryHistory>,
        chosen: ViewState,
    ): NewsstandUiState {
        val sources = prefs.enabledNewsSources
        val region = chosen.region?.takeIf { r -> sources.any { it.region == r } }
        val selected = chosen.sourceId?.takeIf { id -> sources.any { it.id == id && (region == null || it.region == region) } }
        val shownSources = sources.filter { (region == null || it.region == region) && (selected == null || it.id == selected) }
        val shownIds = shownSources.map { it.id }
        val query = chosen.query.trim()

        fun article(item: FeedItem): NewsArticle? = NewsSources.byId(item.sourceId)?.let { source ->
            val entry = entries[item.canonicalUrl]
            NewsArticle(item, source, progress[item.canonicalUrl], isRead = entry?.isRead == true, isSaved = entry?.isSaved == true)
        }

        var topicCounts = emptyMap<NewsTopic, Int>()
        val stories = if (chosen.saved) {
            entries.values.filter { it.isSaved }
                .sortedByDescending { it.savedAt }
                .mapNotNull { article(it.item) }
                .filter { it.matches(query) }
                .map { NewsStory(it) }
        } else {
            val byRegion = sources.associate { it.id to it.region.ordinal }
            val candidates = feedState.headlines(shownIds) { id -> byRegion[id] ?: 0 }
                .mapNotNull(::article)
                .filter { it.matches(query) && !(prefs.hideReadNews && it.isRead) }
            val topics = candidates.associateWith { TopicClassifier.topics(it.item) }
            topicCounts = NewsTopic.entries.associateWith { topic -> topics.values.count { topic in it } }.filterValues { it > 0 }
            val headlines = if (chosen.topic == null) candidates else candidates.filter { chosen.topic in topics.getValue(it) }
            if (selected != null) {
                headlines.map { NewsStory(it) }
            } else {
                StoryGrouping.group(headlines, title = { it.item.title }, outlet = { it.source.id })
                    .map { NewsStory(it.lead, it.related) }
            }
        }

        val briefing = if (feedState.feeds.isEmpty()) {
            emptyList()
        } else {
            feedState.briefing(sources) { entries[it.canonicalUrl]?.isRead == true }
                .mapNotNull { group -> article(group.lead)?.let { NewsStory(it, group.related.mapNotNull(::article)) } }
        }

        val continueReading = if (chosen.saved || query.isNotEmpty()) {
            emptyList()
        } else {
            entries.values
                .filter { entry -> progress[entry.item.canonicalUrl]?.let { it > 0f && it < 1f } == true }
                .sortedByDescending { it.readAt }
                .take(MAX_CONTINUE_READING)
                .mapNotNull { article(it.item) }
        }

        return NewsstandUiState(
            hasLoaded = chosen.saved || feedState.feeds.keys.any { it in shownIds },
            sources = sources,
            showingSaved = chosen.saved,
            selectedRegion = region,
            selectedSourceId = selected,
            query = chosen.query,
            selectedTopic = chosen.topic,
            topicCounts = topicCounts,
            hideRead = prefs.hideReadNews,
            stories = stories,
            continueReading = continueReading,
            briefing = briefing,
            savedCount = entries.values.count { it.isSaved },
            failedSources = if (chosen.saved) emptyList() else shownSources.filter { it.id in feedState.failedSources },
            isRefreshing = feedState.isRefreshing,
            lastUpdated = if (chosen.saved) null else feedState.lastUpdated(shownIds),
        )
    }

    private fun NewsArticle.matches(query: String): Boolean {
        if (query.isEmpty()) return true
        val needle = query.lowercase(Locale.getDefault())
        return item.title.lowercase(Locale.getDefault()).contains(needle) ||
            item.summary.lowercase(Locale.getDefault()).contains(needle) ||
            source.name.lowercase(Locale.getDefault()).contains(needle)
    }

    private data class ViewState(
        val saved: Boolean = false,
        val region: NewsRegion? = null,
        val sourceId: String? = null,
        val topic: NewsTopic? = null,
        val query: String = "",
    )

    private data class OpeningState(
        val preparingUrl: String? = null,
        val bookToOpen: Long? = null,
        val autoPlay: Boolean = false,
        val message: NewsMessage? = null,
        val refreshedOnce: Boolean = false,
    )

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val MAX_CONTINUE_READING = 10

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as SpeedReadApplication).container
                NewsstandViewModel(
                    container.newsRepository,
                    container.articleRepository,
                    container.newsHistoryRepository,
                    container.preferencesRepository,
                    container.applicationScope,
                    container.networkMonitor.isMetered,
                    container.newsQueue,
                    onBriefingStarted = container.newsNotifications::cancelBriefing,
                )
            }
        }
    }
}
