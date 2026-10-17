package io.github.fianna76.speedread.ui.news

import android.text.format.DateUtils
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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

/** Callbacks of the Newsstand, grouped to keep signatures readable. */
data class NewsstandActions(
    val onSelectSource: (String?) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onReadInApp: (FeedItem) -> Unit = {},
    val onOpenOnline: (FeedItem) -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onMessageShown: (Long) -> Unit = {},
)

@Composable
fun NewsstandScreen(
    onOpenBook: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    bottomBar: @Composable () -> Unit,
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
            onSelectSource = viewModel::selectSource,
            onRefresh = viewModel::refresh,
            onReadInApp = viewModel::readInApp,
            onOpenOnline = { context.openInBrowser(it.url) },
            onOpenSettings = onOpenSettings,
            onMessageShown = viewModel::onMessageShown,
        ),
        bottomBar = bottomBar,
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
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var previewUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    // A different filter is a different list: start it from the top.
    LaunchedEffect(state.selectedSourceId) { listState.scrollToItem(0) }

    state.message?.let { message ->
        val text = message.text()
        val openOnline = stringResource(R.string.news_open_online)
        LaunchedEffect(message.id) {
            val result = snackbarHostState.showSnackbar(text, actionLabel = openOnline, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) actions.onOpenOnline(message.item)
            actions.onMessageShown(message.id)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.news_title)) },
                actions = {
                    IconButton(onClick = actions.onRefresh, enabled = !state.isRefreshing && state.sources.isNotEmpty()) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = stringResource(R.string.news_refresh))
                    }
                    IconButton(onClick = actions.onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.action_settings))
                    }
                },
            )
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.sources.isEmpty()) {
                NewsEmptyState(
                    title = stringResource(R.string.news_no_sources_title),
                    body = stringResource(R.string.news_no_sources_body),
                    button = stringResource(R.string.news_choose_sources),
                    onClick = actions.onOpenSettings,
                )
                return@Column
            }
            SourceFilterRow(state, actions.onSelectSource)
            PullToRefreshBox(
                isRefreshing = state.isRefreshing && state.articles.isNotEmpty(),
                onRefresh = actions.onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    !state.hasLoaded || (state.articles.isEmpty() && state.isRefreshing) ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    state.articles.isEmpty() -> NewsEmptyState(
                        title = stringResource(
                            if (state.failedSources.isNotEmpty()) R.string.news_failed_title else R.string.news_empty_title,
                        ),
                        body = stringResource(
                            if (state.failedSources.isNotEmpty()) R.string.news_failed_body else R.string.news_empty_body,
                        ),
                        button = stringResource(R.string.news_retry),
                        onClick = actions.onRefresh,
                    )
                    else -> LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item(key = "status") { FeedStatus(state, now) }
                        items(state.articles, key = { it.item.canonicalUrl }) { article ->
                            ArticleCard(
                                article = article,
                                now = now,
                                preparing = state.preparingUrl == article.item.canonicalUrl,
                                busy = state.preparingUrl != null,
                                onPreview = { previewUrl = article.item.canonicalUrl },
                                onReadInApp = { actions.onReadInApp(article.item) },
                                onOpenOnline = { actions.onOpenOnline(article.item) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }

    val previewed = previewUrl?.let { url -> state.articles.firstOrNull { it.item.canonicalUrl == url } }
    if (previewed != null) {
        ArticlePreviewSheet(
            article = previewed,
            now = now,
            preparing = state.preparingUrl == previewed.item.canonicalUrl,
            busy = state.preparingUrl != null,
            onDismiss = { previewUrl = null },
            onReadInApp = { actions.onReadInApp(previewed.item) },
            onOpenOnline = {
                previewUrl = null
                actions.onOpenOnline(previewed.item)
            },
        )
    }
    // Leave the preview once the reader opens, so Back from the reader returns to the list.
    LaunchedEffect(state.bookToOpen) { if (state.bookToOpen != null) previewUrl = null }
}

@Composable
private fun SourceFilterRow(state: NewsstandUiState, onSelectSource: (String?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "all") {
            FilterChip(
                selected = state.selectedSourceId == null,
                onClick = { onSelectSource(null) },
                label = { Text(stringResource(R.string.news_all_sources)) },
            )
        }
        items(state.sources, key = { it.id }) { source ->
            FilterChip(
                selected = state.selectedSourceId == source.id,
                onClick = { onSelectSource(if (state.selectedSourceId == source.id) null else source.id) },
                label = { Text(source.name) },
            )
        }
    }
}

@Composable
private fun FeedStatus(state: NewsstandUiState, now: Long) {
    val text = when {
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
private fun ArticleCard(
    article: NewsArticle,
    now: Long,
    preparing: Boolean,
    busy: Boolean,
    onPreview: () -> Unit,
    onReadInApp: () -> Unit,
    onOpenOnline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = article.item
    ElevatedCard(onClick = onPreview, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = sourceLine(article, now),
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
                            .size(88.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
            if (item.summary.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            article.progress?.let { progress -> ReadingProgress(progress) }
            Spacer(Modifier.height(12.dp))
            ArticleButtons(
                preparing = preparing,
                busy = busy,
                progress = article.progress,
                onReadInApp = onReadInApp,
                onOpenOnline = onOpenOnline,
            )
        }
    }
}

@Composable
private fun ReadingProgress(progress: Float) {
    Spacer(Modifier.height(10.dp))
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
private fun ArticleButtons(
    preparing: Boolean,
    busy: Boolean,
    progress: Float?,
    onReadInApp: () -> Unit,
    onOpenOnline: () -> Unit,
    filled: Boolean = false,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
            Button(onClick = onReadInApp, enabled = !busy, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) { content() }
        } else {
            FilledTonalButton(onClick = onReadInApp, enabled = !busy, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                content()
            }
        }
        val openOnline: @Composable () -> Unit = {
            Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.news_open_online))
        }
        if (filled) {
            OutlinedButton(onClick = onOpenOnline, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) { openOnline() }
        } else {
            TextButton(onClick = onOpenOnline, contentPadding = ButtonDefaults.TextButtonWithIconContentPadding) { openOnline() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticlePreviewSheet(
    article: NewsArticle,
    now: Long,
    preparing: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onReadInApp: () -> Unit,
    onOpenOnline: () -> Unit,
) {
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
            article.progress?.let { ReadingProgress(it) }
            Spacer(Modifier.height(8.dp))
            Text(
                ArticleUrls.displayHost(item.url),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            ArticleButtons(
                preparing = preparing,
                busy = busy,
                progress = article.progress,
                onReadInApp = onReadInApp,
                onOpenOnline = onOpenOnline,
                filled = true,
            )
        }
    }
}

@Composable
private fun NewsEmptyState(title: String, body: String, button: String, onClick: () -> Unit) {
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
        Spacer(Modifier.height(24.dp))
        Button(onClick = onClick) { Text(button) }
    }
}

@Composable
private fun sourceLine(article: NewsArticle, now: Long): String {
    val published = article.item.publishedAt ?: return article.source.name
    return stringResource(R.string.news_source_time, article.source.name, relativeTime(published, now))
}

/** "5 min. ago", "2 hr. ago", "Yesterday"…; future timestamps (clock skew) read as "now". */
private fun relativeTime(time: Long, now: Long): String =
    DateUtils.getRelativeTimeSpanString(minOf(time, now), now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
        .toString()

@Composable
private fun NewsMessage.text(): String = when (error) {
    ArticleError.NETWORK -> stringResource(R.string.news_error_network)
    ArticleError.NO_TEXT -> stringResource(R.string.news_error_no_text)
    ArticleError.STORAGE -> stringResource(R.string.news_error_storage)
}
