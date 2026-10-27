package io.github.fianna76.speedread.ui.sports

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
import io.github.fianna76.speedread.data.news.NewsHistoryRepository
import io.github.fianna76.speedread.data.news.NewsRepository
import io.github.fianna76.speedread.data.sports.CalendarExport
import io.github.fianna76.speedread.data.sports.CalendarExportResult
import io.github.fianna76.speedread.data.sports.DeviceCalendar
import io.github.fianna76.speedread.data.sports.SportsCalendarRepository
import io.github.fianna76.speedread.data.settings.UserPreferencesRepository
import io.github.fianna76.speedread.news.FeedItem
import io.github.fianna76.speedread.news.sports.MotorsportCatalog
import io.github.fianna76.speedread.news.sports.NbaTeams
import io.github.fianna76.speedread.news.sports.SportsEvent
import io.github.fianna76.speedread.news.sports.SportsNews
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A sports story, with the followed teams or series it's about. */
data class SportsStory(
    val item: FeedItem,
    val outlet: String,
    /** "Lakers", "F1": what made this story show up. */
    val about: List<String>,
    val isRead: Boolean = false,
    val progress: Float? = null,
)

data class SportsUiState(
    val teams: Set<String> = emptySet(),
    val series: Set<String> = emptySet(),
    val stories: List<SportsStory> = emptyList(),
    /** Upcoming events for what's followed, soonest first. */
    val events: List<SportsEvent> = emptyList(),
    /** Followed teams or series whose calendar couldn't be loaded. */
    val calendarFailures: List<String> = emptyList(),
    val isRefreshing: Boolean = false,
    val hasLoaded: Boolean = false,
    val preparingUrl: String? = null,
    val bookToOpen: Long? = null,
    val message: SportsMessage? = null,
) {
    val followsNothing: Boolean get() = teams.isEmpty() && series.isEmpty()
}

sealed interface SportsMessage {
    val id: Long

    data class ArticleFailed(override val id: Long, val item: FeedItem, val error: ArticleError) : SportsMessage

    data class Exported(override val id: Long, val calendar: String, val result: CalendarExportResult) : SportsMessage

    data class ExportFailed(override val id: Long) : SportsMessage
}

/** Writes events to a device calendar; see [CalendarExport]. */
fun interface CalendarWriter {
    suspend fun write(calendar: DeviceCalendar, events: List<SportsEvent>): CalendarExportResult
}

/** Labels for what's followed: team nicknames and series short names. */
fun followLabel(id: String): String = NbaTeams.byAbbreviation(id)?.nickname ?: MotorsportCatalog.byId(id)?.shortName ?: id

class SportsViewModel(
    private val preferences: UserPreferencesRepository,
    private val news: NewsRepository,
    private val calendar: SportsCalendarRepository,
    private val articles: ArticleRepository,
    private val history: NewsHistoryRepository,
    private val externalScope: CoroutineScope,
    private val writer: CalendarWriter? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val follows = preferences.preferences.map { it.followedNbaTeams to it.followedSeries }.distinctUntilChanged()
    private val transient = MutableStateFlow(Transient())
    private var nextMessageId = 0L

    private data class Transient(
        val preparingUrl: String? = null,
        val bookToOpen: Long? = null,
        val message: SportsMessage? = null,
        val loaded: Boolean = false,
    )

    private val content = combine(
        follows,
        news.state,
        calendar.state,
        history.observe(),
        articles.observeProgress(),
    ) { (teams, series), feeds, calendars, read, progress ->
        val ids = SportsNews.sources(teams, series).associateBy { it.id }
        val stories = feeds.headlines(ids.keys).mapNotNull { item ->
            val about = SportsNews.follows(item, teams, series)
            if (about.isEmpty()) return@mapNotNull null
            SportsStory(
                item = item,
                outlet = ids[item.sourceId]?.name.orEmpty(),
                about = about.map(::followLabel).sorted(),
                isRead = read[item.canonicalUrl]?.isRead == true,
                progress = progress[item.canonicalUrl],
            )
        }
        val failed = calendars.failed.mapNotNull { key ->
            val id = key.removePrefix("nba:")
            if (id in teams || id in series) followLabel(id) else null
        }
        SportsUiState(
            teams = teams,
            series = series,
            stories = stories,
            events = calendars.upcoming(teams, series, clock()).take(MAX_EVENTS),
            calendarFailures = failed,
            isRefreshing = feeds.isRefreshing || calendars.isRefreshing,
            hasLoaded = feeds.feeds.keys.any { it in ids } || calendars.calendars.isNotEmpty(),
        )
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<SportsUiState> = combine(content, transient) { state, extra ->
        state.copy(
            hasLoaded = state.hasLoaded || extra.loaded || state.followsNothing,
            preparingUrl = extra.preparingUrl,
            bookToOpen = extra.bookToOpen,
            message = extra.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SportsUiState())

    init {
        viewModelScope.launch {
            follows.collect { (teams, series) ->
                val sources = SportsNews.sources(teams, series)
                launch { news.refreshIfStale(sources) }
                launch { calendar.refresh(teams, series) }.join()
                transient.update { it.copy(loaded = true) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val (teams, series) = uiState.value.teams to uiState.value.series
            launch { news.refresh(SportsNews.sources(teams, series)) }
            launch { calendar.refresh(teams, series, force = true) }
        }
    }

    fun setTeamFollowed(team: String, followed: Boolean) {
        externalScope.launch { preferences.setNbaTeamFollowed(team, followed) }
    }

    fun setSeriesFollowed(series: String, followed: Boolean) {
        externalScope.launch { preferences.setSeriesFollowed(series, followed) }
    }

    fun readInApp(item: FeedItem) {
        if (transient.value.preparingUrl != null) return
        transient.update { it.copy(preparingUrl = item.canonicalUrl) }
        viewModelScope.launch {
            val result = try {
                articles.open(item)
            } finally {
                transient.update { it.copy(preparingUrl = null) }
            }
            when (result) {
                is ArticleResult.Ready -> {
                    externalScope.launch { history.markRead(item) }
                    transient.update { it.copy(bookToOpen = result.bookId) }
                }
                is ArticleResult.Failed -> post { id -> SportsMessage.ArticleFailed(id, item, result.reason) }
            }
        }
    }

    fun onOpenedOnline(item: FeedItem) {
        externalScope.launch { history.markRead(item) }
    }

    /** Adds [events] (by default every upcoming one shown) to [target]. */
    fun addToCalendar(target: DeviceCalendar, events: List<SportsEvent> = uiState.value.events) {
        val writer = writer ?: return
        viewModelScope.launch {
            try {
                val result = writer.write(target, events)
                post { id -> SportsMessage.Exported(id, target.name, result) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                post { id -> SportsMessage.ExportFailed(id) }
            }
        }
    }

    fun onReaderOpened() = transient.update { it.copy(bookToOpen = null) }

    fun onMessageShown(id: Long) = transient.update { if (it.message?.id == id) it.copy(message = null) else it }

    private fun post(create: (Long) -> SportsMessage) = transient.update { it.copy(message = create(nextMessageId++)) }

    companion object {
        /** Enough for weeks of NBA games plus a season of race weekends. */
        const val MAX_EVENTS = 400

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SpeedReadApplication
                val container = app.container
                SportsViewModel(
                    container.preferencesRepository,
                    container.sportsNewsRepository,
                    container.sportsCalendarRepository,
                    container.articleRepository,
                    container.newsHistoryRepository,
                    container.applicationScope,
                    writer = { target, events -> CalendarExport.addAll(app.contentResolver, target.id, events) },
                )
            }
        }
    }
}
