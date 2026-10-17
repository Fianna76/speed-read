package io.github.fianna76.speedread.ui.news

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import io.github.fianna76.speedread.R
import io.github.fianna76.speedread.data.news.ArticleError
import io.github.fianna76.speedread.news.ArticleUrls
import io.github.fianna76.speedread.news.FeedItem
import io.github.fianna76.speedread.news.NewsRegion
import io.github.fianna76.speedread.news.NewsSource

/** Callbacks of the Newsstand, grouped to keep signatures readable. */
data class NewsstandActions(
    val onSelectRegion: (NewsRegion?) -> Unit = {},
    val onSelectSource: (String?) -> Unit = {},
    val onShowSaved: (Boolean) -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onHideReadChange: (Boolean) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onReadInApp: (FeedItem) -> Unit = {},
    val onOpenOnline: (FeedItem) -> Unit = {},
    val onSetSaved: (FeedItem, Boolean) -> Unit = { _, _ -> },
    val onShare: (FeedItem) -> Unit = {},
    val onHideSource: (NewsSource) -> Unit = {},
    val onUndoHideSource: (NewsSource) -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onMessageShown: (Long) -> Unit = {},
)

/**
 * @param scrollToTop increases each time the News tab is tapped while already selected; the
 * list then scrolls back to the top.
 */
@Composable
fun NewsstandScreen(
    onOpenBook: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    bottomBar: @Composable () -> Unit,
    scrollToTop: Int = 0,
    viewModel: NewsstandViewModel = viewModel(factory = NewsstandViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    state.bookToOpen?.let { bookId ->
        LaunchedEffect(bookId) {
            viewModel.onReaderOpened()
            onOpenBook(bookId)
        }
    }

    NewsstandContent(
        state = state,
        now = System.currentTimeMillis(),
        actions = NewsstandActions(
            onSelectRegion = viewModel::selectRegion,
            onSelectSource = viewModel::selectSource,
            onShowSaved = viewModel::showSaved,
            onQueryChange = viewModel::setQuery,
            onHideReadChange = viewModel::setHideRead,
            onRefresh = viewModel::refresh,
            onReadInApp = viewModel::readInApp,
            onOpenOnline = { item ->
                viewModel.onOpenedOnline(item)
                context.openInBrowser(item.url)
            },
            onSetSaved = viewModel::setSaved,
            onShare = { context.shareStory(it) },
            onHideSource = viewModel::hideSource,
            onUndoHideSource = viewModel::undoHideSource,
            onOpenSettings = onOpenSettings,
            onMessageShown = viewModel::onMessageShown,
        ),
        bottomBar = bottomBar,
        scrollToTop = scrollToTop,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsstandContent(
    state: NewsstandUiState,
    now: Long,
    actions: NewsstandActions,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    scrollToTop: Int = 0,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var previewUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var searching by rememberSaveable { mutableStateOf(state.isSearching) }
    val listState = rememberLazyListState()
    // A different filter is a different list: start it from the top.
    LaunchedEffect(state.showingSaved, state.selectedRegion, state.selectedSourceId, state.query) { listState.scrollToItem(0) }
    LaunchedEffect(scrollToTop) { if (scrollToTop > 0) listState.animateScrollToItem(0) }

    fun closeSearch() {
        searching = false
        actions.onQueryChange("")
    }
    BackHandler(enabled = searching, onBack = ::closeSearch)

    state.message?.let { message -> MessageSnackbar(message, snackbarHostState, actions) }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (searching) {
                SearchTopBar(query = state.query, onQueryChange = actions.onQueryChange, onClose = ::closeSearch)
            } else {
                NewsTopBar(state, actions, onSearch = { searching = true })
            }
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.sources.isEmpty() && !state.showingSaved) {
                NewsEmptyState(
                    title = stringResource(R.string.news_no_sources_title),
                    body = stringResource(R.string.news_no_sources_body),
                    button = stringResource(R.string.news_choose_sources),
                    onClick = actions.onOpenSettings,
                )
                return@Column
            }
            FilterRows(state, actions)
            PullToRefreshBox(
                isRefreshing = state.isRefreshing && state.stories.isNotEmpty(),
                onRefresh = actions.onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    !state.hasLoaded || (state.stories.isEmpty() && state.isRefreshing && !state.showingSaved) ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    state.stories.isEmpty() && state.continueReading.isEmpty() -> EmptyList(state, actions)
                    else -> LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item(key = "status") { FeedStatus(state, now) }
                        if (state.continueReading.isNotEmpty()) {
                            item(key = "continue") {
                                ContinueReading(state.continueReading, state.preparingUrl != null, actions.onReadInApp)
                            }
                        }
                        items(state.stories, key = { it.article.item.canonicalUrl }) { story ->
                            StoryCard(
                                story = story,
                                now = now,
                                preparing = state.preparingUrl == story.article.item.canonicalUrl,
                                busy = state.preparingUrl != null,
                                actions = actions,
                                onPreview = { previewUrl = story.article.item.canonicalUrl },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }

    val previewed = previewUrl?.let { url -> findStory(state, url) }
    if (previewed != null) {
        StoryPreviewSheet(
            story = previewed,
            now = now,
            preparing = state.preparingUrl == previewed.article.item.canonicalUrl,
            busy = state.preparingUrl != null,
            actions = actions,
            onShowVersion = { previewUrl = it.item.canonicalUrl },
            onDismiss = { previewUrl = null },
            onOpenOnline = {
                previewUrl = null
                actions.onOpenOnline(previewed.article.item)
            },
        )
    }
    // Leave the preview once the reader opens, so Back from the reader returns to the list.
    LaunchedEffect(state.bookToOpen) { if (state.bookToOpen != null) previewUrl = null }
}

/** The story to preview: a card's lead, or one of its grouped versions (shown with its siblings). */
private fun findStory(state: NewsstandUiState, url: String): NewsStory? {
    state.stories.firstOrNull { it.article.item.canonicalUrl == url }?.let { return it }
    for (story in state.stories) {
        val version = story.related.firstOrNull { it.item.canonicalUrl == url } ?: continue
        return NewsStory(version, listOf(story.article) + story.related.filter { it !== version })
    }
    return state.continueReading.firstOrNull { it.item.canonicalUrl == url }?.let { NewsStory(it) }
}

@Composable
private fun MessageSnackbar(message: NewsMessage, host: SnackbarHostState, actions: NewsstandActions) {
    val text = when (message) {
        is NewsMessage.ArticleFailed -> when (message.error) {
            ArticleError.NETWORK -> stringResource(R.string.news_error_network)
            ArticleError.NO_TEXT -> stringResource(R.string.news_error_no_text)
            ArticleError.PAYWALLED -> stringResource(R.string.news_error_paywalled)
            ArticleError.STORAGE -> stringResource(R.string.news_error_storage)
        }
        is NewsMessage.NewStories -> if (message.count == 0) {
            stringResource(R.string.news_no_new_stories)
        } else {
            pluralStringResource(R.plurals.news_new_stories, message.count, message.count)
        }
        is NewsMessage.SourceHidden -> stringResource(R.string.news_source_hidden, message.source.name)
    }
    val action = when (message) {
        is NewsMessage.ArticleFailed -> stringResource(R.string.news_open_online)
        is NewsMessage.SourceHidden -> stringResource(R.string.news_undo)
        is NewsMessage.NewStories -> null
    }
    LaunchedEffect(message.id) {
        val duration = if (message is NewsMessage.NewStories) SnackbarDuration.Short else SnackbarDuration.Long
        val result = host.showSnackbar(text, actionLabel = action, duration = duration)
        if (result == SnackbarResult.ActionPerformed) {
            when (message) {
                is NewsMessage.ArticleFailed -> actions.onOpenOnline(message.item)
                is NewsMessage.SourceHidden -> actions.onUndoHideSource(message.source)
                is NewsMessage.NewStories -> Unit
            }
        }
        actions.onMessageShown(message.id)
    }
}

// --- Top bars ------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewsTopBar(state: NewsstandUiState, actions: NewsstandActions, onSearch: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(stringResource(R.string.news_title)) },
        actions = {
            IconButton(onClick = onSearch) {
                Icon(painterResource(R.drawable.ic_search), contentDescription = stringResource(R.string.news_search))
            }
            IconButton(onClick = actions.onRefresh, enabled = !state.isRefreshing && state.sources.isNotEmpty()) {
                Icon(painterResource(R.drawable.ic_refresh), contentDescription = stringResource(R.string.news_refresh))
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.news_more_options))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.news_hide_read)) },
                        trailingIcon = { Checkbox(checked = state.hideRead, onCheckedChange = null) },
                        onClick = {
                            menuOpen = false
                            actions.onHideReadChange(!state.hideRead)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.news_choose_sources)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_settings), contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            actions.onOpenSettings()
                        },
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.news_close_search))
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.news_search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.news_clear_search))
                }
            }
        },
    )
}

// --- Filters -------------------------------------------------------------------------------------

/** Region chips (All, Saved, Ireland, Europe, World), then the outlets of the selected region. */
@Composable
private fun FilterRows(state: NewsstandUiState, actions: NewsstandActions) {
    ChipRow {
        item(key = "all") {
            FilterChip(
                selected = !state.showingSaved && state.selectedRegion == null,
                onClick = { actions.onSelectRegion(null) },
                label = { Text(stringResource(R.string.news_all_sources)) },
            )
        }
        item(key = "saved") {
            FilterChip(
                selected = state.showingSaved,
                onClick = { actions.onShowSaved(!state.showingSaved) },
                leadingIcon = {
                    Icon(painterResource(R.drawable.ic_bookmark_filled), contentDescription = null, modifier = Modifier.size(18.dp))
                },
                label = {
                    Text(
                        if (state.savedCount > 0) {
                            stringResource(R.string.news_saved_count, state.savedCount)
                        } else {
                            stringResource(R.string.news_saved)
                        },
                    )
                },
            )
        }
        if (state.regions.size > 1) {
            items(state.regions, key = { it.name }) { region ->
                FilterChip(
                    selected = !state.showingSaved && state.selectedRegion == region,
                    onClick = { actions.onSelectRegion(if (state.selectedRegion == region && !state.showingSaved) null else region) },
                    label = { Text(stringResource(region.labelRes())) },
                )
            }
        }
    }
    if (!state.showingSaved) {
        ChipRow {
            items(state.sourcesInRegion, key = { it.id }) { source ->
                FilterChip(
                    selected = state.selectedSourceId == source.id,
                    onClick = { actions.onSelectSource(if (state.selectedSourceId == source.id) null else source.id) },
                    label = { Text(source.name) },
                )
            }
        }
    }
}

@Composable
private fun ChipRow(content: LazyListScope.() -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
        content = content,
    )
}

fun NewsRegion.labelRes(): Int = when (this) {
    NewsRegion.IRELAND -> R.string.news_region_ireland
    NewsRegion.EUROPE -> R.string.news_region_europe
    NewsRegion.WORLD -> R.string.news_region_world
}

// --- List content --------------------------------------------------------------------------------

@Composable
private fun FeedStatus(state: NewsstandUiState, now: Long) {
    val text = when {
        state.isSearching -> pluralStringResource(R.plurals.news_search_results, state.stories.size, state.stories.size)
        state.showingSaved -> stringResource(R.string.news_saved_explained)
        state.failedSources.size > MAX_NAMED_FAILURES -> {
            val count = state.failedSources.size
            pluralStringResource(R.plurals.news_many_sources_failed, count, count)
        }
        state.failedSources.isNotEmpty() ->
            stringResource(R.string.news_sources_failed, state.failedSources.joinToString(", ") { it.name })
        state.lastUpdated != null && now - state.lastUpdated < DateUtils.MINUTE_IN_MILLIS ->
            stringResource(R.string.news_updated_just_now)
        state.lastUpdated != null -> stringResource(R.string.news_updated, relativeTime(state.lastUpdated, now))
        else -> return
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (state.failedSources.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ContinueReading(articles: List<NewsArticle>, busy: Boolean, onRead: (FeedItem) -> Unit) {
    Column {
        Text(
            text = stringResource(R.string.news_continue_reading),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(articles, key = { it.item.canonicalUrl }) { article ->
                OutlinedCard(
                    onClick = { onRead(article.item) },
                    enabled = !busy,
                    modifier = Modifier.width(232.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            article.source.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            article.item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            minLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        article.progress?.let { ReadingProgress(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryCard(
    story: NewsStory,
    now: Long,
    preparing: Boolean,
    busy: Boolean,
    actions: NewsstandActions,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val article = story.article
    val item = article.item
    val dimmed = article.isRead && article.progress?.let { it < 1f } != true
    ElevatedCard(onClick = onPreview, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 4.dp)) {
            Row(Modifier.padding(end = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = sourceLine(article, now),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.imageUrl != null) {
                    Spacer(Modifier.width(12.dp))
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
            if (item.summary.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            // Downloaded but unstarted articles (e.g. saved ones) have nothing to show yet.
            article.progress?.takeIf { it > 0f }?.let { Box(Modifier.padding(end = 8.dp)) { ReadingProgress(it) } }
            if (story.related.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = alsoCoveredBy(story.related),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpeedReadButton(preparing = preparing, busy = busy, progress = article.progress, filled = false) {
                    actions.onReadInApp(item)
                }
                Spacer(Modifier.weight(1f))
                SaveButton(article.isSaved) { actions.onSetSaved(item, !article.isSaved) }
                IconButton(onClick = { actions.onOpenOnline(item) }) {
                    Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = stringResource(R.string.news_open_online))
                }
                MoreMenu(article, actions)
            }
        }
    }
}

@Composable
private fun SaveButton(saved: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painter = painterResource(if (saved) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark),
            contentDescription = stringResource(if (saved) R.string.news_unsave else R.string.news_save),
            tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MoreMenu(article: NewsArticle, actions: NewsstandActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.news_story_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.news_share)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_share), contentDescription = null) },
                onClick = {
                    open = false
                    actions.onShare(article.item)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.news_hide_source, article.source.name)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_visibility_off), contentDescription = null) },
                onClick = {
                    open = false
                    actions.onHideSource(article.source)
                },
            )
        }
    }
}

@Composable
private fun ReadingProgress(progress: Float) {
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (progress >= 1f) {
                stringResource(R.string.book_finished)
            } else {
                stringResource(R.string.news_progress, (progress * 100).toInt())
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SpeedReadButton(preparing: Boolean, busy: Boolean, progress: Float?, filled: Boolean, onClick: () -> Unit) {
    val label = stringResource(
        when {
            preparing -> R.string.news_preparing
            progress != null && progress > 0f && progress < 1f -> R.string.news_continue
            else -> R.string.news_speed_read
        },
    )
    val content: @Composable () -> Unit = {
        if (preparing) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(painterResource(R.drawable.ic_bolt), contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
    if (filled) {
        Button(onClick = onClick, enabled = !busy, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) { content() }
    } else {
        FilledTonalButton(onClick = onClick, enabled = !busy, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) { content() }
    }
}

// --- Preview sheet -------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoryPreviewSheet(
    story: NewsStory,
    now: Long,
    preparing: Boolean,
    busy: Boolean,
    actions: NewsstandActions,
    onShowVersion: (NewsArticle) -> Unit,
    onDismiss: () -> Unit,
    onOpenOnline: () -> Unit,
) {
    val article = story.article
    val item = article.item
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            if (item.imageUrl != null) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Spacer(Modifier.height(16.dp))
            }
            Text(sourceLine(article, now), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(item.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            item.author?.let { author ->
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.news_byline, author),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.summary.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(item.summary, style = MaterialTheme.typography.bodyLarge)
            }
            article.progress?.takeIf { it > 0f }?.let { ReadingProgress(it) }
            Spacer(Modifier.height(8.dp))
            Text(
                ArticleUrls.displayHost(item.url),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SpeedReadButton(preparing = preparing, busy = busy, progress = article.progress, filled = true) {
                    actions.onReadInApp(item)
                }
                OutlinedButton(onClick = onOpenOnline, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                    Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.news_open_online))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SaveButton(article.isSaved) { actions.onSetSaved(item, !article.isSaved) }
                Text(
                    stringResource(if (article.isSaved) R.string.news_saved_offline else R.string.news_save_for_later),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { actions.onShare(item) }) {
                    Icon(painterResource(R.drawable.ic_share), contentDescription = stringResource(R.string.news_share))
                }
            }
            if (story.related.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.news_other_versions),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() },
                )
                story.related.forEach { version ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onShowVersion(version) }
                            .padding(vertical = 10.dp),
                    ) {
                        Text(sourceLine(version, now), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(version.item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// --- Empty states --------------------------------------------------------------------------------

@Composable
private fun EmptyList(state: NewsstandUiState, actions: NewsstandActions) {
    when {
        state.isSearching -> NewsEmptyState(
            title = stringResource(R.string.news_search_empty_title, state.query.trim()),
            body = stringResource(R.string.news_search_empty_body),
            button = null,
            onClick = {},
        )
        state.showingSaved -> NewsEmptyState(
            title = stringResource(R.string.news_saved_empty_title),
            body = stringResource(R.string.news_saved_empty_body),
            button = null,
            onClick = {},
        )
        state.hideRead -> NewsEmptyState(
            title = stringResource(R.string.news_all_read_title),
            body = stringResource(R.string.news_all_read_body),
            button = stringResource(R.string.news_show_read),
            onClick = { actions.onHideReadChange(false) },
        )
        else -> NewsEmptyState(
            title = stringResource(if (state.failedSources.isNotEmpty()) R.string.news_failed_title else R.string.news_empty_title),
            body = stringResource(if (state.failedSources.isNotEmpty()) R.string.news_failed_body else R.string.news_empty_body),
            button = stringResource(R.string.news_retry),
            onClick = actions.onRefresh,
        )
    }
}

@Composable
private fun NewsEmptyState(title: String, body: String, button: String?, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_newspaper),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp),
        )
        if (button != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onClick) { Text(button) }
        }
    }
}

// --- Formatting ----------------------------------------------------------------------------------

/** Beyond this many unreachable outlets the status line gives a count instead of names. */
private const val MAX_NAMED_FAILURES = 3

/** Outlets named in "Also covered by …" before the rest are counted. */
private const val MAX_NAMED_VERSIONS = 2

@Composable
private fun alsoCoveredBy(related: List<NewsArticle>): String {
    val names = related.map { it.source.name }.distinct()
    val named = names.take(MAX_NAMED_VERSIONS).joinToString(", ")
    val more = names.size - MAX_NAMED_VERSIONS
    return if (more > 0) {
        stringResource(R.string.news_also_covered_more, named, more)
    } else {
        stringResource(R.string.news_also_covered, named)
    }
}

@Composable
private fun sourceLine(article: NewsArticle, now: Long): String {
    val published = article.item.publishedAt
    val line = if (published == null) {
        article.source.name
    } else {
        stringResource(R.string.news_source_time, article.source.name, relativeTime(published, now))
    }
    return if (article.isRead) stringResource(R.string.news_source_time, line, stringResource(R.string.news_read)) else line
}

/** "5 min. ago", "2 hr. ago", "Yesterday"…; future timestamps (clock skew) read as "now". */
private fun relativeTime(time: Long, now: Long): String =
    DateUtils.getRelativeTimeSpanString(minOf(time, now), now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
        .toString()
