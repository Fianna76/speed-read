package io.github.fianna76.studypack.ui.rsvp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fianna76.speedread.rsvp.ContextWindow
import io.github.fianna76.speedread.rsvp.Pacing
import io.github.fianna76.speedread.rsvp.PlayerState
import io.github.fianna76.studypack.R
import io.github.fianna76.studypack.data.pdf.SectionKind
import io.github.fianna76.studypack.ui.components.BackButton
import io.github.fianna76.studypack.ui.components.EmptyState
import java.text.NumberFormat
import kotlin.math.roundToInt

private const val FONT_SIZE_SP = 40

@Composable
fun RsvpScreen(
    noteId: Long,
    startPage: Int?,
    onBack: () -> Unit,
    viewModel: RsvpViewModel = viewModel(factory = RsvpViewModel.factory(noteId, startPage)),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val player by viewModel.playerState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.pause() }
    KeepScreenOn(player.isPlaying)
    RsvpContent(
        ui = ui,
        player = player,
        actions = RsvpActions(
            onBack = onBack,
            onTogglePlay = viewModel::togglePlayPause,
            onPause = viewModel::pause,
            onPreviousSentence = viewModel::previousSentence,
            onNextSentence = viewModel::nextSentence,
            onPreviousChapter = viewModel::previousChapter,
            onNextChapter = viewModel::nextChapter,
            onSelectChapter = viewModel::selectChapter,
            onScrub = viewModel::scrubTo,
            onScrubFinished = viewModel::scrubFinished,
            onSeekToWord = viewModel::seekToWord,
            onSpeed = viewModel::setWordsPerMinute,
            onSpeedFinished = viewModel::commitWordsPerMinute,
            onSpeedBy = viewModel::changeWordsPerMinuteBy,
            onRestart = viewModel::restart,
        ),
    )
}

/** Callbacks of the reader, bundled to keep [RsvpContent]'s signature manageable. */
class RsvpActions(
    val onBack: () -> Unit = {},
    val onTogglePlay: () -> Unit = {},
    val onPause: () -> Unit = {},
    val onPreviousSentence: () -> Unit = {},
    val onNextSentence: () -> Unit = {},
    val onPreviousChapter: () -> Unit = {},
    val onNextChapter: () -> Unit = {},
    val onSelectChapter: (Int) -> Unit = {},
    val onScrub: (Float) -> Unit = {},
    val onScrubFinished: () -> Unit = {},
    val onSeekToWord: (Int) -> Unit = {},
    val onSpeed: (Int) -> Unit = {},
    val onSpeedFinished: () -> Unit = {},
    val onSpeedBy: (Int) -> Unit = {},
    val onRestart: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RsvpContent(ui: RsvpUiState, player: PlayerState, actions: RsvpActions, initialPageView: Boolean = false) {
    var showSections by rememberSaveable { mutableStateOf(false) }
    var pageView by rememberSaveable { mutableStateOf(initialPageView) }
    // Playing always shows the speed-reading view.
    LaunchedEffect(player.isPlaying) { if (player.isPlaying) pageView = false }
    val chapterTitle = ui.chapter?.displayTitle()

    Scaffold(
        // Insets are handled per component so the control panel can extend behind the navigation bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(actions.onBack) },
                title = {
                    Column {
                        Text(ui.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (chapterTitle != null && !ui.isLoading) {
                            Text(
                                chapterTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    // Page view: the section as ordinary text, to scroll and tap a word to continue from.
                    IconButton(
                        onClick = {
                            if (!pageView && player.isPlaying) actions.onPause()
                            pageView = !pageView
                        },
                        enabled = ui.canControl,
                    ) {
                        Icon(
                            painterResource(if (pageView) R.drawable.ic_bolt else R.drawable.ic_menu_book),
                            stringResource(if (pageView) R.string.rsvp_rsvp_view else R.string.rsvp_page_view),
                        )
                    }
                    IconButton(onClick = { showSections = true }, enabled = !ui.isLoading && ui.chapters.size > 1) {
                        Icon(painterResource(R.drawable.ic_toc), stringResource(R.string.rsvp_sections))
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
                    ui.isLoading -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.rsvp_loading), modifier = Modifier.padding(top = 16.dp))
                    }
                    ui.problem != null -> EmptyState(
                        icon = R.drawable.ic_info,
                        title = ui.title,
                        body = stringResource(if (ui.problem == RsvpProblem.NO_TEXT) R.string.rsvp_no_text else R.string.rsvp_failed),
                        modifier = Modifier.align(Alignment.Center),
                    )
                    ui.isFinished -> Finished(ui, actions, Modifier.align(Alignment.Center))
                    pageView -> PageView(
                        text = player.text,
                        currentIndex = player.index,
                        // Sections usually open with their own heading; don't repeat it above.
                        header = chapterTitle?.takeUnless { title -> player.text.startsWithWords(title) },
                        onWordTapped = { index ->
                            actions.onSeekToWord(index)
                            pageView = false
                        },
                    )
                    else -> {
                        ReadingSurface(ui, player)
                        TapZones(
                            isPlaying = player.isPlaying,
                            enabled = ui.canControl,
                            onPreviousSentence = actions.onPreviousSentence,
                            onPlayPause = actions.onTogglePlay,
                            onNextSentence = actions.onNextSentence,
                        )
                    }
                }
            }
            if (ui.problem == null && !ui.isLoading) {
                Controls(ui, player, actions)
            }
        }
    }

    if (showSections) {
        SectionSheet(
            chapters = ui.chapters,
            currentIndex = ui.chapterIndex,
            settings = player.settings,
            onSelect = { index ->
                showSections = false
                pageView = false
                actions.onSelectChapter(index)
            },
            onDismiss = { showSections = false },
        )
    }
}

@Composable
private fun Controls(ui: RsvpUiState, player: PlayerState, actions: RsvpActions) {
    val chapter = ui.chapter
    val bodyTotal = ui.bodyWords.coerceAtLeast(1)
    val read = when {
        ui.isFinished -> bodyTotal
        chapter?.kind == SectionKind.BODY -> ui.bodyWordsBefore(ui.chapterIndex) + player.index
        else -> ui.bodyWordsBefore(ui.chapterIndex)
    }
    val percent = (read * 100f / bodyTotal).roundToInt().coerceIn(0, 100)
    val left = formatDuration(Pacing.estimateMillis((bodyTotal - read).coerceAtLeast(0), player.settings))
        ?: stringResource(R.string.rsvp_less_than_a_minute)
    ReaderControls(
        sectionLabel = if (ui.chapters.size > 1) stringResource(R.string.rsvp_section_position, ui.chapterIndex + 1, ui.chapters.size) else "",
        progressLabel = stringResource(R.string.rsvp_progress, percent, left),
        chapterProgress = if (ui.isFinished) 1f else player.progress,
        wordsPerMinute = player.settings.wordsPerMinute,
        isPlaying = player.isPlaying,
        enabled = ui.canControl,
        hasPreviousChapter = ui.hasPreviousChapter,
        hasNextChapter = ui.hasNextChapter,
        actions = actions,
    )
}

/** The word strip at a fixed height, with the surrounding text (or a usage hint) underneath while paused. */
@Composable
private fun ReadingSurface(ui: RsvpUiState, player: PlayerState) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        RsvpWordView(player.displayWord, FONT_SIZE_SP)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            when {
                player.isPlaying -> Unit
                ui.showTapHint -> Text(
                    text = stringResource(R.string.rsvp_tap_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 420.dp),
                )
                else -> {
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
            if (context.truncatedStart) append("… ")
            context.words.forEachIndexed { i, word ->
                if (i > 0) append(' ')
                if (i == context.currentIndex) {
                    withStyle(SpanStyle(color = highlight, fontWeight = FontWeight.Bold)) { append(word) }
                } else {
                    append(word)
                }
            }
            if (context.truncatedEnd) append(" …")
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
private fun Finished(ui: RsvpUiState, actions: RsvpActions, modifier: Modifier = Modifier) {
    val words = remember(ui.bodyWords) { NumberFormat.getIntegerInstance().format(ui.bodyWords) }
    EmptyState(
        icon = R.drawable.ic_check,
        title = stringResource(R.string.rsvp_finished_title),
        body = stringResource(R.string.rsvp_finished_body, words),
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = actions.onRestart) { Text(stringResource(R.string.rsvp_read_again)) }
            TextButton(onClick = actions.onBack) { Text(stringResource(R.string.back)) }
        }
    }
}

/** Keeps the screen on while words are flashing. */
@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

/** Whether the text begins with the words of [phrase] (ignoring case and spacing). */
internal fun io.github.fianna76.speedread.rsvp.TokenizedText.startsWithWords(phrase: String): Boolean {
    val wanted = phrase.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (wanted.isEmpty() || wanted.size > size) return false
    return wanted.indices.all { words[it].text.equals(wanted[it], ignoreCase = true) }
}
