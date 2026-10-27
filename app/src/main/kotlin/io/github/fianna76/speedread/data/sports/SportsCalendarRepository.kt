package io.github.fianna76.speedread.data.sports

import android.util.Log
import io.github.fianna76.speedread.data.news.HttpStatusException
import io.github.fianna76.speedread.data.news.WebClient
import io.github.fianna76.speedread.news.sports.CalendarSource
import io.github.fianna76.speedread.news.sports.EspnNbaSchedule
import io.github.fianna76.speedread.news.sports.IcsCalendar
import io.github.fianna76.speedread.news.sports.LeMansCalendar
import io.github.fianna76.speedread.news.sports.MotorsportCatalog
import io.github.fianna76.speedread.news.sports.MotorsportSeries
import io.github.fianna76.speedread.news.sports.NbaTeams
import io.github.fianna76.speedread.news.sports.SportsEvent
import io.github.fianna76.speedread.news.sports.SportsTimesCalendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.ZoneOffset

/** Calendars by source (`nba:<team>` or a series id), with when each was fetched. */
data class SportsCalendarState(
    val calendars: Map<String, SourceCalendar> = emptyMap(),
    /** Sources whose last refresh failed (older events, if any, are kept). */
    val failed: Set<String> = emptySet(),
    val isRefreshing: Boolean = false,
) {
    /** Upcoming (or in-progress) events for the followed teams and series, soonest first. */
    fun upcoming(teams: Set<String>, series: Set<String>, now: Long): List<SportsEvent> {
        val nba = teams.flatMap { calendars[nbaKey(it)]?.events.orEmpty() }
        val motorsport = series.flatMap { calendars[it]?.events.orEmpty() }
        // A game between two followed teams is in both schedules: keep one.
        return (nba + motorsport).filter { it.endMillis > now }.distinctBy { it.id }.sortedBy { it.startMillis }
    }

    companion object {
        fun nbaKey(team: String) = "nba:$team"
    }
}

data class SourceCalendar(val events: List<SportsEvent>, val fetchedAt: Long)

/**
 * Fetches and caches the sports calendars: each followed NBA team's season (ESPN's public team
 * schedule, about 35 KB compressed) and each followed series' calendar (this season, plus next
 * season from October). Calendars change rarely, so each is refreshed at most every
 * [STALE_AFTER_MILLIS].
 */
class SportsCalendarRepository(
    private val web: WebClient,
    private val cacheDir: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _state = MutableStateFlow(SportsCalendarState())
    val state: StateFlow<SportsCalendarState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var cacheLoaded = false

    /** Le Mans series race pages (by URL) and season pages' race lists, cached between refreshes. */
    private val racePages = HashMap<String, SourceCalendar>()
    private val seasonPages = HashMap<String, Pair<Long, List<String>>>()

    /** Refreshes the calendars needed for [teams] and [series] that are missing or stale (all of them if [force]). */
    suspend fun refresh(teams: Set<String>, series: Set<String>, force: Boolean = false) = mutex.withLock {
        loadCache()
        val now = clock()
        val wanted = teams.map(SportsCalendarState::nbaKey) + series.filter { MotorsportCatalog.byId(it)?.calendar != null }
        val due = wanted.filter { key ->
            val fetchedAt = _state.value.calendars[key]?.fetchedAt
            force || fetchedAt == null || now - fetchedAt >= STALE_AFTER_MILLIS || fetchedAt > now
        }
        if (due.isEmpty()) return@withLock
        _state.update { it.copy(isRefreshing = true) }
        try {
            val results = coroutineScope { due.map { key -> async { key to fetch(key, now) } }.awaitAll() }
            _state.update { state ->
                val calendars = state.calendars.toMutableMap()
                val failed = state.failed.toMutableSet()
                for ((key, events) in results) {
                    if (events == null) {
                        failed += key
                    } else {
                        calendars[key] = SourceCalendar(events, now)
                        failed -= key
                    }
                }
                state.copy(calendars = calendars, failed = failed)
            }
            withContext(ioDispatcher) { writeCache(_state.value.calendars) }
        } finally {
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    private suspend fun fetch(key: String, now: Long): List<SportsEvent>? = try {
        if (key.startsWith(NBA_PREFIX)) fetchNba(key.removePrefix(NBA_PREFIX)) else MotorsportCatalog.byId(key)?.let { fetchSeries(it, now) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Could not load the $key calendar", e)
        null
    }

    private suspend fun fetchNba(team: String): List<SportsEvent>? {
        val espnId = NbaTeams.byAbbreviation(team)?.espnId ?: return null
        val json = web.fetchText("$NBA_TEAMS/$espnId/schedule")
        return withContext(Dispatchers.Default) { EspnNbaSchedule.parse(json) }
    }

    private suspend fun fetchSeries(series: MotorsportSeries, now: Long): List<SportsEvent> {
        val today = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC)
        val year = today.year
        // From October, next season's calendar is usually out (missing files are fine).
        val years = if (today.monthValue >= 10) listOf(year, year + 1) else listOf(year)
        return when (val source = series.calendar) {
            is CalendarSource.SportsTimes -> {
                val texts = years.mapIndexedNotNull { index, y ->
                    try {
                        web.fetchText(source.url(y))
                    } catch (e: HttpStatusException) {
                        if (index == 0) throw e else null // Next season not published yet.
                    }
                }
                withContext(Dispatchers.Default) { texts.flatMap { SportsTimesCalendar.parse(it, series) } }
            }
            is CalendarSource.Ics -> {
                val text = web.fetchText(source.url)
                withContext(Dispatchers.Default) { IcsCalendar.parse(text, series) }
            }
            is CalendarSource.LeMansSite -> fetchLeMans(series, source, years, now)
            null -> emptyList()
        }
    }

    /**
     * Season pages (about 1 MB) are read weekly for their race list. Each race page (about
     * 250 KB) is read once, then again twice a day in the three weeks before the race (session
     * times firm up) or weekly before that. Past races are never fetched again.
     */
    private suspend fun fetchLeMans(series: MotorsportSeries, source: CalendarSource.LeMansSite, years: List<Int>, now: Long): List<SportsEvent> {
        val paths = years.flatMapIndexed { index, year ->
            val url = source.seasonUrl(year)
            val cached = seasonPages[url]
            if (cached != null && now - cached.first < WEEK_MILLIS) return@flatMapIndexed cached.second
            try {
                val list = withContext(Dispatchers.Default) { LeMansCalendar.racePaths(web.fetchText(url)) }
                seasonPages[url] = now to list
                list
            } catch (e: HttpStatusException) {
                if (index == 0) throw e else emptyList() // Next season not published yet.
            }
        }.distinct().filter { (LeMansCalendar.yearOf(it) ?: years.first()) >= years.first() }

        val events = ArrayList<SportsEvent>()
        for (path in paths) {
            val url = source.raceUrl(path)
            val cached = racePages[url]
            val past = cached != null && cached.events.isNotEmpty() && cached.events.all { it.endMillis < now }
            val soon = cached?.events?.any { it.startMillis - now < THREE_WEEKS_MILLIS } ?: true
            val due = cached == null || (!past && now - cached.fetchedAt >= if (soon) STALE_AFTER_MILLIS else WEEK_MILLIS)
            val page = if (due) {
                try {
                    val parsed = withContext(Dispatchers.Default) { LeMansCalendar.parseRace(web.fetchText(url), series) }
                    SourceCalendar(parsed, now).also { racePages[url] = it }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Could not read race page $url", e)
                    cached // Keep what we had.
                }
            } else {
                cached
            }
            page?.let { events += it.events }
        }
        if (events.isEmpty() && paths.isNotEmpty() && paths.none { racePages.containsKey(source.raceUrl(it)) }) {
            throw java.io.IOException("No race page could be read")
        }
        return events
    }

    private suspend fun loadCache() {
        if (cacheLoaded) return
        cacheLoaded = true
        val cached = withContext(ioDispatcher) { readCache() } ?: return
        cached.races.forEach { racePages[it.key] = SourceCalendar(it.events.map(CachedEvent::toEvent), it.fetchedAt) }
        cached.seasons.forEach { seasonPages[it.url] = it.fetchedAt to it.paths }
        val calendars = cached.calendars.associate { it.key to SourceCalendar(it.events.map(CachedEvent::toEvent), it.fetchedAt) }
        _state.update { it.copy(calendars = calendars + it.calendars) }
    }

    private fun cacheFile() = File(cacheDir, "calendars.json")

    private fun readCache(): CachedFile? = runCatching {
        val file = cacheFile()
        if (!file.isFile) null else JSON.decodeFromString<CachedFile>(file.readText())
    }.onFailure { Log.w(TAG, "Discarding unreadable calendar cache", it) }.getOrNull()

    private fun writeCache(calendars: Map<String, SourceCalendar>) {
        runCatching {
            val file = cacheFile()
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            val cached = CachedFile(
                calendars = calendars.map { (key, calendar) -> CachedCalendar(key, calendar.fetchedAt, calendar.events.map(CachedEvent::from)) },
                races = racePages.map { (url, page) -> CachedCalendar(url, page.fetchedAt, page.events.map(CachedEvent::from)) },
                seasons = seasonPages.map { (url, page) -> CachedSeason(url, page.first, page.second) },
            )
            temp.writeText(JSON.encodeToString(cached))
            if (!temp.renameTo(file)) temp.delete()
        }.onFailure { Log.w(TAG, "Could not cache calendars", it) }
    }

    @Serializable
    private data class CachedFile(
        val calendars: List<CachedCalendar> = emptyList(),
        val races: List<CachedCalendar> = emptyList(),
        val seasons: List<CachedSeason> = emptyList(),
    )

    @Serializable
    private data class CachedSeason(val url: String, val fetchedAt: Long, val paths: List<String>)

    @Serializable
    private data class CachedCalendar(val key: String, val fetchedAt: Long, val events: List<CachedEvent>)

    @Serializable
    private data class CachedEvent(
        val id: String,
        val title: String,
        val detail: String? = null,
        val start: Long,
        val minutes: Int,
        val venue: String? = null,
        val follows: Set<String>,
        val kind: String,
        val allDay: Boolean = false,
    ) {
        fun toEvent() = SportsEvent(id, title, detail, start, minutes, venue, follows, SportsEvent.Kind.valueOf(kind), allDay)

        companion object {
            fun from(e: SportsEvent) = CachedEvent(e.id, e.title, e.detail, e.startMillis, e.durationMinutes, e.venue, e.follows, e.kind.name, e.allDay)
        }
    }

    companion object {
        private const val TAG = "SportsCalendar"
        const val STALE_AFTER_MILLIS = 12 * 60 * 60 * 1000L
        private const val WEEK_MILLIS = 7 * 24 * 60 * 60 * 1000L
        private const val THREE_WEEKS_MILLIS = 3 * WEEK_MILLIS
        const val NBA_TEAMS = "https://site.api.espn.com/apis/site/v2/sports/basketball/nba/teams"
        private const val NBA_PREFIX = "nba:"
        private val JSON = Json { ignoreUnknownKeys = true }
    }
}
