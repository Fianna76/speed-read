package io.github.fianna76.speedread.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fianna76.speedread.R
import io.github.fianna76.speedread.data.Chapter
import io.github.fianna76.speedread.rsvp.ContextWindow
import io.github.fianna76.speedread.rsvp.PlayerState
import io.github.fianna76.speedread.rsvp.Tokenizer
import io.github.fianna76.speedread.ui.news.openInBrowser
import io.github.fianna76.speedread.ui.theme.SpeedReadTheme
import java.text.NumberFormat
import kotlin.math.roundToInt

@Composable
fun ReaderScreen(
    bookId: Long,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = viewModel(factory = ReaderViewModel.factory(bookId)),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val player by viewModel.playerState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onBackgrounded() }
    KeepScreenOn(player.isPlaying)

    ReaderContent(
        ui = ui,
        player = player,
        actions = ReaderActions(
            onBack = onBack,
            onPlayPause = viewModel::togglePlayPause,
            onPreviousSentence = viewModel::skipBackward,
            onNextSentence = viewModel::skipForward,
            onPreviousChapter = viewModel::previousChapter,
            onNextChapter = viewModel::nextChapter,
            onSelectChapter = viewModel::selectChapter,
            onWordsPerMinuteChange = viewModel::setWordsPerMinute,
            onWordsPerMinuteChangeFinished = viewModel::commitWordsPerMinute,
            onChangeWordsPerMinuteBy = viewModel::changeWordsPerMinuteBy,
            onRestart = viewModel::restartBook,
            onRetry = viewModel::retry,
            onOpenOnline = { url ->
                viewModel.onBackgrounded() // Pause and save before leaving for the browser.
                context.openInBrowser(url)
            },
        ),
    )
}

/** Callbacks of the reader, bundled to keep [ReaderContent]'s signature manageable. */
class ReaderActions(
    val onBack: () -> Unit = {},
    val onPlayPause: () -> Unit = {},
    val onPreviousSentence: () -> Unit = {},
    val onNextSentence: () -> Unit = {},
    val onPreviousChapter: () -> Unit = {},
    val onNextChapter: () -> Unit = {},
    val onSelectChapter: (Int) -> Unit = {},
    val onWordsPerMinuteChange: (Int) -> Unit = {},
    val onWordsPerMinuteChangeFinished: () -> Unit = {},
    val onChangeWordsPerMinuteBy: (Int) -> Unit = {},
    val onRestart: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onOpenOnline: (String) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderContent(ui: ReaderUiState, player: PlayerState, actions: ReaderActions, modifier: Modifier = Modifier) {
    var showChapters by rememberSaveable { mutableStateOf(false) }
    val progress = ReaderProgress.of(ui, player)
    val canControl = !ui.isLoading && ui.error == null && !ui.isFinished

    Scaffold(
        modifier = modifier,
        // Insets are handled per component so the control panel can extend behind the navigation bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
                title = {
                    Column {
                        Text(ui.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        (ui.sourceName ?: ui.chapter?.displayTitle())?.let { subtitle ->
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    val sourceUrl = ui.sourceUrl
                    if (sourceUrl != null) {
                        // Articles are a single section: offer the original page instead of a contents list.
                        IconButton(onClick = { actions.onOpenOnline(sourceUrl) }) {
                            Icon(painterResource(R.drawable.ic_open_in_new), stringResource(R.string.news_open_online))
                        }
                    } else {
                        IconButton(onClick = { showChapters = true }, enabled = ui.chapters.isNotEmpty()) {
                            Icon(painterResource(R.drawable.ic_toc), stringResource(R.string.reader_chapters))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            ) {
                when {
                    ui.error != null -> ReaderMessage(
                        iconRes = R.drawable.ic_info,
                        title = stringResource(R.string.reader_error_title),
                        body = stringResource(ui.error.messageRes()),
                        primaryLabel = stringResource(R.string.reader_back_to_library),
                        onPrimary = actions.onBack,
                        secondaryLabel = if (ui.error == ReaderError.LOAD_FAILED) stringResource(R.string.reader_retry) else null,
                        onSecondary = actions.onRetry,
                    )
                    ui.isFinished -> ReaderMessage(
                        iconRes = R.drawable.ic_check_circle,
                        title = stringResource(R.string.reader_finished_title),
                        body = stringResource(
                            R.string.reader_finished_body,
                            remember(ui.totalWords) { NumberFormat.getIntegerInstance().format(ui.totalWords) },
                        ),
                        primaryLabel = stringResource(R.string.reader_back_to_library),
                        onPrimary = actions.onBack,
                        secondaryLabel = stringResource(R.string.reader_read_again),
                        onSecondary = actions.onRestart,
                    )
                    else -> {
                        ReadingSurface(ui, player)
                        TapZones(
                            isPlaying = player.isPlaying,
                            enabled = canControl,
                            onPreviousSentence = actions.onPreviousSentence,
                            onPlayPause = actions.onPlayPause,
                            onNextSentence = actions.onNextSentence,
                        )
                    }
                }
            }

            if (ui.error == null) {
                val percent = (progress.bookFraction * 100).roundToInt()
                val timeLeft = formatDuration(progress.bookMillisLeft) ?: stringResource(R.string.reader_less_than_a_minute)
                ReaderControls(
                    chapterLabel = when {
                        ui.sourceUrl != null -> pluralStringResource(R.plurals.reader_article_words, ui.totalWords, ui.totalWords)
                        ui.chapters.size <= 1 -> ""
                        else -> stringResource(R.string.reader_chapter_position, ui.chapterIndex + 1, ui.chapters.size)
                    },
                    progressLabel = if (ui.isFinished) {
                        stringResource(R.string.book_finished)
                    } else {
                        stringResource(R.string.reader_book_progress, percent, timeLeft)
                    },
                    chapterProgress = progress.chapterFraction,
                    wordsPerMinute = player.settings.wordsPerMinute,
                    isPlaying = player.isPlaying,
                    enabled = canControl,
                    hasPreviousChapter = ui.hasPreviousChapter,
                    hasNextChapter = ui.hasNextChapter,
                    onWordsPerMinuteChange = actions.onWordsPerMinuteChange,
                    onWordsPerMinuteChangeFinished = actions.onWordsPerMinuteChangeFinished,
                    onChangeWordsPerMinuteBy = actions.onChangeWordsPerMinuteBy,
                    onPlayPause = actions.onPlayPause,
                    onPreviousSentence = actions.onPreviousSentence,
                    onNextSentence = actions.onNextSentence,
                    onPreviousChapter = actions.onPreviousChapter,
                    onNextChapter = actions.onNextChapter,
                )
            }
        }
    }

    if (showChapters) {
        ChapterSheet(
            chapters = ui.chapters,
            currentIndex = ui.chapterIndex,
            settings = player.settings,
            onSelect = { index ->
                showChapters = false
                actions.onSelectChapter(index)
            },
            onDismiss = { showChapters = false },
        )
    }
}

/** The word strip, pinned at a fixed height, with context or a usage hint underneath. */
@Composable
private fun ReadingSurface(ui: ReaderUiState, player: PlayerState) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        if (ui.isLoading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            RsvpWordView(word = player.word, display = ui.display)
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            when {
                ui.isLoading || player.isPlaying -> Unit
                ui.showTapHint -> Text(
                    text = stringResource(R.string.reader_tap_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 420.dp),
                )
                ui.display.showContextWhenPaused -> {
                    val context = remember(player.text, player.index) { player.text.contextAround(player.index) }
                    PausedContext(context, Modifier.widthIn(max = 560.dp))
                }
            }
        }
    }
}

/** The text around the current word, shown while paused so readers can re-orient themselves. */
@Composable
private fun PausedContext(context: ContextWindow, modifier: Modifier = Modifier) {
    if (context.words.isEmpty()) return
    val highlight = MaterialTheme.colorScheme.primary
    val text = remember(context, highlight) {
        buildAnnotatedString {
            if (context.truncatedStart) append("\u2026 ")
            context.words.forEachIndexed { i, word ->
                if (i > 0) append(' ')
                if (i == context.currentIndex) {
                    withStyle(SpanStyle(color = highlight, fontWeight = FontWeight.Bold)) { append(word) }
                } else {
                    append(word)
                }
            }
            if (context.truncatedEnd) append(" \u2026")
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 6,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun ReaderMessage(
    iconRes: Int,
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String?,
    onSecondary: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onPrimary) { Text(primaryLabel) }
        if (secondaryLabel != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onSecondary) { Text(secondaryLabel) }
        }
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

private fun ReaderError.messageRes(): Int = when (this) {
    ReaderError.BOOK_NOT_FOUND -> R.string.reader_error_missing
    ReaderError.NO_TEXT -> R.string.reader_error_no_text
    ReaderError.LOAD_FAILED -> R.string.reader_error_load
}

@Preview(showBackground = true, widthDp = 400, heightDp = 760)
@Composable
private fun ReaderPreview() {
    val text = Tokenizer.tokenize(listOf("It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife."))
    SpeedReadTheme(dynamicColor = false) {
        ReaderContent(
            ui = ReaderUiState(
                isLoading = false,
                title = "Pride and Prejudice",
                chapters = listOf(Chapter(0, "Chapter 1", "c1.xhtml", text.size, 0)),
                totalWords = 120_000,
            ),
            player = PlayerState(text = text, index = 3),
            actions = ReaderActions(),
        )
    }
}
