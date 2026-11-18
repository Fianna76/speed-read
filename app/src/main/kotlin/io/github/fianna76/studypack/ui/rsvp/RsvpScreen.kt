package io.github.fianna76.studypack.ui.rsvp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import io.github.fianna76.speedread.rsvp.WordsPerMinute
import io.github.fianna76.studypack.R
import io.github.fianna76.studypack.ui.components.BackButton
import io.github.fianna76.studypack.ui.components.EmptyState
import io.github.fianna76.studypack.ui.theme.tabular

private const val FONT_SIZE_SP = 40

@Composable
fun RsvpScreen(
    noteId: Long,
    startPage: Int,
    onBack: () -> Unit,
    viewModel: RsvpViewModel = viewModel(factory = RsvpViewModel.factory(noteId, startPage)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.pause() }
    RsvpContent(
        state = state,
        actions = RsvpActions(
            onBack = onBack,
            onTogglePlay = viewModel::togglePlayPause,
            onPrevious = viewModel::previousSentence,
            onNext = viewModel::nextSentence,
            onSpeed = viewModel::changeSpeed,
            onSeek = viewModel::seekTo,
        ),
    )
}

class RsvpActions(
    val onBack: () -> Unit = {},
    val onTogglePlay: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onSpeed: (Int) -> Unit = {},
    val onSeek: (Float) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RsvpContent(state: RsvpUiState, actions: RsvpActions) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(actions.onBack) },
                title = {
                    Text(
                        if (state.title.isEmpty()) "" else stringResource(R.string.rsvp_title, state.title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.rsvp_loading), modifier = Modifier.padding(top = 16.dp))
                }
                state.problem != null -> EmptyState(
                    icon = R.drawable.ic_info,
                    title = state.title,
                    body = stringResource(if (state.problem == RsvpProblem.NO_TEXT) R.string.rsvp_no_text else R.string.rsvp_failed),
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> Reader(state, actions)
            }
        }
    }
}

@Composable
private fun Reader(state: RsvpUiState, actions: RsvpActions) {
    Column(Modifier.fillMaxSize()) {
        // Tapping the reading area plays or pauses.
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = actions.onTogglePlay),
            verticalArrangement = Arrangement.Center,
        ) {
            RsvpWordView(state.word, FONT_SIZE_SP)
            state.context?.let { ContextText(it) }
        }
        Controls(state, actions)
    }
}

@Composable
private fun ContextText(context: ContextWindow) {
    val text = buildAnnotatedString {
        if (context.truncatedStart) append("… ")
        context.words.forEachIndexed { i, word ->
            if (i > 0) append(' ')
            if (i == context.currentIndex) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) { append(word) }
            } else {
                append(word)
            }
        }
        if (context.truncatedEnd) append(" …")
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
    )
}

@Composable
private fun Controls(state: RsvpUiState, actions: RsvpActions) {
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        var dragging by remember { mutableStateOf(false) }
        var dragValue by remember { mutableFloatStateOf(0f) }
        val positionLabel = stringResource(R.string.rsvp_position)
        Slider(
            value = if (dragging) dragValue else state.progress,
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                actions.onSeek(dragValue)
                dragging = false
            },
            modifier = Modifier.semantics { contentDescription = positionLabel },
        )
        Text(
            stringResource(R.string.rsvp_page, state.page + 1, state.pageCount),
            style = MaterialTheme.typography.bodyMedium.tabular,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(onClick = { actions.onSpeed(-WordsPerMinute.STEP) }, enabled = state.wordsPerMinute > WordsPerMinute.MIN) {
                Icon(painterResource(R.drawable.ic_fast_rewind), contentDescription = stringResource(R.string.rsvp_slower))
            }
            IconButton(onClick = actions.onPrevious) {
                Icon(painterResource(R.drawable.ic_skip_previous), contentDescription = stringResource(R.string.rsvp_previous_sentence))
            }
            FilledIconButton(onClick = actions.onTogglePlay, modifier = Modifier.size(64.dp)) {
                Icon(
                    painterResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                    contentDescription = stringResource(if (state.isPlaying) R.string.rsvp_pause else R.string.rsvp_play),
                )
            }
            IconButton(onClick = actions.onNext) {
                Icon(painterResource(R.drawable.ic_skip_next), contentDescription = stringResource(R.string.rsvp_next_sentence))
            }
            IconButton(onClick = { actions.onSpeed(WordsPerMinute.STEP) }, enabled = state.wordsPerMinute < WordsPerMinute.MAX) {
                Icon(painterResource(R.drawable.ic_fast_forward), contentDescription = stringResource(R.string.rsvp_faster))
            }
        }
        Text(
            stringResource(R.string.rsvp_wpm, state.wordsPerMinute),
            style = MaterialTheme.typography.labelLarge.tabular,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .heightIn(min = 24.dp),
        )
        Spacer(Modifier.size(8.dp))
    }
}
