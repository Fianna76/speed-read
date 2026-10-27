package io.github.fianna76.speedread.ui.sports

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fianna76.speedread.R
import io.github.fianna76.speedread.data.news.ArticleError
import io.github.fianna76.speedread.data.sports.CalendarExport
import io.github.fianna76.speedread.data.sports.DeviceCalendar
import io.github.fianna76.speedread.news.FeedItem
import io.github.fianna76.speedread.news.sports.MotorsportCatalog
import io.github.fianna76.speedread.news.sports.MotorsportSeries
import io.github.fianna76.speedread.news.sports.NbaTeam
import io.github.fianna76.speedread.news.sports.NbaTeams
import io.github.fianna76.speedread.news.sports.SportsEvent
import io.github.fianna76.speedread.ui.news.openInBrowser
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Callbacks of the Sports section. */
data class SportsActions(
    val onBack: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onReadInApp: (FeedItem) -> Unit = {},
    val onOpenOnline: (FeedItem) -> Unit = {},
    val onTeamFollowed: (String, Boolean) -> Unit = { _, _ -> },
    val onSeriesFollowed: (String, Boolean) -> Unit = { _, _ -> },
    /** One event into the calendar app (no permission needed). */
    val onAddEvent: (SportsEvent) -> Unit = {},
    /** Every shown event into a chosen calendar. */
    val onAddAll: (List<SportsEvent>) -> Unit = {},
    val onMessageShown: (Long) -> Unit = {},
)

/** The Sports section: news and an events calendar for followed NBA teams and motorsport series. */
@Composable
fun SportsScreen(
    onBack: () -> Unit,
    onOpenBook: (Long) -> Unit,
    viewModel: SportsViewModel = viewModel(factory = SportsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var calendars by remember { mutableStateOf<List<DeviceCalendar>?>(null) }
    var pendingExport by remember { mutableStateOf<List<SportsEvent>>(emptyList()) }
    val noCalendarApp = stringResource(R.string.sports_no_calendar_app)
    val noCalendars = stringResource(R.string.sports_no_calendars)
    val permissionDenied = stringResource(R.string.sports_calendar_permission_denied)

    fun pickCalendar() {
        scope.launch {
            val writable = CalendarExport.writableCalendars(context.contentResolver)
            if (writable.isEmpty()) Toast.makeText(context, noCalendars, Toast.LENGTH_LONG).show() else calendars = writable
        }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) pickCalendar() else Toast.makeText(context, permissionDenied, Toast.LENGTH_LONG).show()
    }

    state.bookToOpen?.let { bookId ->
        LaunchedEffect(bookId) {
            viewModel.onReaderOpened()
            onOpenBook(bookId)
        }
    }

    SportsContent(
        state = state,
        now = System.currentTimeMillis(),
        zone = ZoneId.systemDefault(),
        actions = SportsActions(
            onBack = onBack,
            onRefresh = viewModel::refresh,
            onReadInApp = viewModel::readInApp,
            onOpenOnline = { item ->
                viewModel.onOpenedOnline(item)
                context.openInBrowser(item.url)
            },
            onTeamFollowed = viewModel::setTeamFollowed,
            onSeriesFollowed = viewModel::setSeriesFollowed,
            onAddEvent = { event ->
                try {
                    context.startActivity(CalendarExport.insertIntent(event))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, noCalendarApp, Toast.LENGTH_LONG).show()
                }
            },
            onAddAll = { events ->
                pendingExport = events
                val granted = CALENDAR_PERMISSIONS.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
                if (granted) pickCalendar() else permissions.launch(CALENDAR_PERMISSIONS)
            },
            onMessageShown = viewModel::onMessageShown,
        ),
    )

    calendars?.let { options ->
        CalendarPickerDialog(
            calendars = options,
            eventCount = pendingExport.size,
            onPick = { target ->
                calendars = null
                viewModel.addToCalendar(target, pendingExport)
            },
            onDismiss = { calendars = null },
        )
    }
}

private val CALENDAR_PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportsContent(
    state: SportsUiState,
    now: Long,
    zone: ZoneId,
    actions: SportsActions,
    modifier: Modifier = Modifier,
    /** 0 for Latest, 1 for Calendar. */
    initialTab: Int = 0,
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    state.message?.let { message ->
        val text = message.text()
        LaunchedEffect(message.id) {
            snackbar.showSnackbar(text)
            actions.onMessageShown(message.id)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sports_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back)) }
                },
                actions = {
                    if (!state.followsNothing) {
                        IconButton(onClick = actions.onRefresh) { Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.news_refresh)) }
                        IconButton(onClick = { choosing = true }) { Icon(painterResource(R.drawable.ic_tune), stringResource(R.string.sports_following)) }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.followsNothing) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = stringResource(R.string.sports_intro),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp),
                    )
                    FollowPicker(state, actions)
                }
                return@Column
            }
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.sports_latest)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.sports_calendar)) })
            }
            PullToRefreshBox(isRefreshing = state.isRefreshing && state.hasLoaded, onRefresh = actions.onRefresh, modifier = Modifier.fillMaxSize()) {
                when {
                    !state.hasLoaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    tab == 0 -> LatestList(state, now, actions)
                    else -> CalendarList(state, now, zone, actions)
                }
            }
        }
    }

    if (choosing) {
        ModalBottomSheet(onDismissRequest = { choosing = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Text(
                stringResource(R.string.sports_following),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Column(Modifier.verticalScroll(rememberScrollState())) { FollowPicker(state, actions) }
        }
    }
}

@Composable
private fun LatestList(state: SportsUiState, now: Long, actions: SportsActions) {
    if (state.stories.isEmpty()) {
        Message(stringResource(if (state.isRefreshing) R.string.sports_loading else R.string.sports_no_stories))
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.stories, key = { it.item.canonicalUrl }) { story -> StoryCard(story, now, state.preparingUrl, actions) }
    }
}

@Composable
private fun StoryCard(story: SportsStory, now: Long, preparingUrl: String?, actions: SportsActions) {
    val item = story.item
    val dim = story.isRead
    ElevatedCard(onClick = { actions.onReadInApp(item) }, enabled = preparingUrl == null, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp)) {
            Text(
                text = (story.about + story.outlet + listOfNotNull(item.publishedAt?.let { relative(it, now) })).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 8.dp),
            )
            if (item.summary.isNotEmpty()) {
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp, end = 8.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { actions.onReadInApp(item) }, enabled = preparingUrl == null) {
                    if (preparingUrl == item.canonicalUrl) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(painterResource(R.drawable.ic_bolt), contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.news_speed_read))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { actions.onOpenOnline(item) }) {
                    Icon(painterResource(R.drawable.ic_open_in_new), stringResource(R.string.news_open_online))
                }
            }
        }
    }
}

/**
 * Upcoming events, grouped by day in the user's time zone, with start times in their local
 * time. Chips narrow it to one team or series; events can go into the calendar one at a time
 * or all at once.
 */
@Composable
private fun CalendarList(state: SportsUiState, now: Long, zone: ZoneId, actions: SportsActions) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val events = state.events.filter { filter == null || filter in it.follows }
    val locale = LocalConfiguration.current.locales[0]
    val timeFormat = remember(locale) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale) }
    val dayFormat = remember(locale) { DateTimeFormatter.ofPattern("EEEE d MMMM", locale) }
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val newsOnly = state.series.mapNotNull(MotorsportCatalog::byId).filter { it.calendar == null }.map { it.shortName }
    val follows = (state.series.mapNotNull(MotorsportCatalog::byId).filter { it.calendar != null }.map { it.id } + state.teams.sorted())

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
        item(key = "header") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.sports_times_local, zone.id.replace('_', ' ')),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.calendarFailures.isNotEmpty()) {
                    Text(
                        stringResource(R.string.sports_calendar_failed, state.calendarFailures.joinToString(", ")),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (newsOnly.isNotEmpty()) {
                    Text(
                        stringResource(R.string.sports_news_only, newsOnly.joinToString(", ")),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (follows.size > 1) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text(stringResource(R.string.library_filter_all)) }) }
                        items(follows) { id ->
                            FilterChip(selected = filter == id, onClick = { filter = if (filter == id) null else id }, label = { Text(followLabel(id)) })
                        }
                    }
                }
                if (events.isNotEmpty()) {
                    OutlinedButton(onClick = { actions.onAddAll(events) }) {
                        Icon(painterResource(R.drawable.ic_calendar_add), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(pluralStringResource(R.plurals.sports_add_all, events.size, events.size))
                    }
                }
            }
        }
        if (events.isEmpty()) {
            item(key = "empty") { Message(stringResource(if (state.isRefreshing) R.string.sports_loading else R.string.sports_no_events)) }
        }
        val byDay = events.groupBy { event ->
            if (event.allDay) Instant.ofEpochMilli(event.startMillis).atZone(ZoneOffset.UTC).toLocalDate() else Instant.ofEpochMilli(event.startMillis).atZone(zone).toLocalDate()
        }
        byDay.forEach { (day, dayEvents) ->
            item(key = "day-$day") {
                Text(
                    text = dayLabel(day, today, dayFormat),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                        .semantics { heading() },
                )
            }
            items(dayEvents, key = { "event-${it.id}" }) { event -> EventRow(event, zone, timeFormat, actions) }
        }
    }
}

@Composable
private fun EventRow(event: SportsEvent, zone: ZoneId, timeFormat: DateTimeFormatter, actions: SportsActions) {
    val time = if (event.allDay) stringResource(R.string.sports_all_day) else Instant.ofEpochMilli(event.startMillis).atZone(zone).format(timeFormat)
    ListItem(
        leadingContent = {
            Text(
                time,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 64.dp),
            )
        },
        headlineContent = { Text(event.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = listOfNotNull(event.detail, event.venue).takeIf { it.isNotEmpty() }?.let { lines ->
            { Text(lines.joinToString(" · "), maxLines = 2, overflow = TextOverflow.Ellipsis) }
        },
        trailingContent = {
            IconButton(onClick = { actions.onAddEvent(event) }) {
                Icon(painterResource(R.drawable.ic_calendar_add), stringResource(R.string.sports_add_event, event.title))
            }
        },
    )
}

/** Teams and series to follow, as chips grouped by series type and conference. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FollowPicker(state: SportsUiState, actions: SportsActions) {
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.sports_motorsport))
        MotorsportSeries.Group.entries.forEach { group ->
            Text(stringResource(group.labelRes()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MotorsportCatalog.ALL.filter { it.group == group }.forEach { series ->
                    val on = series.id in state.series
                    FilterChip(
                        selected = on,
                        onClick = { actions.onSeriesFollowed(series.id, !on) },
                        label = { Text(series.name) },
                    )
                }
            }
        }
        Text(
            stringResource(R.string.sports_news_only_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionTitle(stringResource(R.string.sports_nba))
        NbaTeam.Conference.entries.forEach { conference ->
            Text(stringResource(conference.labelRes()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NbaTeams.ALL.filter { it.conference == conference }.sortedBy { it.fullName }.forEach { team ->
                    val on = team.abbreviation in state.teams
                    FilterChip(selected = on, onClick = { actions.onTeamFollowed(team.abbreviation, !on) }, label = { Text(team.fullName) })
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
}

@Composable
private fun CalendarPickerDialog(calendars: List<DeviceCalendar>, eventCount: Int, onPick: (DeviceCalendar) -> Unit, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(calendars.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(R.plurals.sports_add_all, eventCount, eventCount)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.sports_pick_calendar), style = MaterialTheme.typography.bodyMedium)
                calendars.forEach { calendar ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { selected = calendar }.padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = calendar == selected, onClick = { selected = calendar })
                        Column {
                            Text(calendar.name)
                            Text(calendar.account, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(selected) }) { Text(stringResource(R.string.sports_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(32.dp),
    )
}

@Composable
private fun SportsMessage.text(): String = when (this) {
    is SportsMessage.ArticleFailed -> stringResource(
        when (error) {
            ArticleError.NETWORK -> R.string.news_error_network
            ArticleError.NO_TEXT -> R.string.shared_link_no_text
            ArticleError.PAYWALLED -> R.string.news_error_paywalled
            ArticleError.STORAGE -> R.string.news_error_storage
        },
    )
    is SportsMessage.Exported -> if (result.added + result.updated == 0) {
        stringResource(R.string.sports_export_up_to_date, calendar)
    } else {
        pluralStringResource(R.plurals.sports_exported, result.added + result.updated, result.added + result.updated, calendar)
    }
    is SportsMessage.ExportFailed -> stringResource(R.string.sports_export_failed)
}

@Composable
private fun dayLabel(day: LocalDate, today: LocalDate, format: DateTimeFormatter): String = when (day) {
    today -> stringResource(R.string.sports_today, day.format(format))
    today.plusDays(1) -> stringResource(R.string.sports_tomorrow, day.format(format))
    else -> day.format(format)
}

private fun relative(time: Long, now: Long): String =
    DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()

private fun MotorsportSeries.Group.labelRes(): Int = when (this) {
    MotorsportSeries.Group.FORMULA -> R.string.sports_group_formula
    MotorsportSeries.Group.ENDURANCE -> R.string.sports_group_endurance
    MotorsportSeries.Group.TOURING -> R.string.sports_group_touring
}

private fun NbaTeam.Conference.labelRes(): Int = when (this) {
    NbaTeam.Conference.EAST -> R.string.sports_east
    NbaTeam.Conference.WEST -> R.string.sports_west
}
