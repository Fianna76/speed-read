package io.github.fianna76.studypack.ui.quiz

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fianna76.studypack.R
import io.github.fianna76.studypack.quiz.MathMarkup
import io.github.fianna76.studypack.quiz.MathSegment
import io.github.fianna76.studypack.quiz.SessionQuestion
import io.github.fianna76.studypack.ui.components.EmptyState
import io.github.fianna76.studypack.ui.components.MathText
import io.github.fianna76.studypack.ui.navigation.QuizRoute
import io.github.fianna76.studypack.ui.theme.StudyTheme
import io.github.fianna76.studypack.ui.theme.tabular

@Composable
fun QuizScreen(
    route: QuizRoute,
    onExit: () -> Unit,
    onRetry: (QuizRoute) -> Unit,
    viewModel: QuizViewModel = viewModel(factory = QuizViewModel.factory(route)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    QuizContent(
        state = state,
        actions = QuizActions(
            onChoose = { option ->
                val current = state.current
                if (current != null && !state.finished) {
                    when {
                        state.examMode -> haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        state.currentAnswer == null -> haptics.performHapticFeedback(
                            if (option == current.correctIndex) HapticFeedbackType.Confirm else HapticFeedbackType.Reject,
                        )
                    }
                }
                viewModel.choose(option)
            },
            onNext = viewModel::next,
            onEndEarly = viewModel::endEarly,
            onExit = onExit,
            onRetryMissed = { viewModel.retryRoute()?.let(onRetry) },
            onNewQuiz = onExit,
        ),
    )
}

class QuizActions(
    val onChoose: (Int) -> Unit = {},
    val onNext: () -> Unit = {},
    val onEndEarly: () -> Unit = {},
    val onExit: () -> Unit = {},
    val onRetryMissed: () -> Unit = {},
    val onNewQuiz: () -> Unit = {},
)

@Composable
fun QuizContent(state: QuizUiState, actions: QuizActions) {
    when {
        state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        state.failed -> FailedContent(actions)
        state.finished -> ResultsContent(state, actions)
        else -> QuestionContent(state, actions)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FailedContent(actions: QuizActions) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = actions.onExit) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.close))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = R.drawable.ic_info,
                title = stringResource(R.string.quiz_empty),
                body = stringResource(R.string.quiz_loading_failed),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestionContent(state: QuizUiState, actions: QuizActions) {
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    BackHandler { confirmEnd = true }
    val question = state.current ?: return
    val scroll = rememberScrollState()
    LaunchedEffect(state.index) { scroll.scrollTo(0) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { confirmEnd = true }) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.quiz_end_early))
                        }
                    },
                    title = {
                        Column {
                            Text(
                                stringResource(R.string.quiz_progress, state.index + 1, state.questions.size),
                                style = MaterialTheme.typography.titleLarge.tabular,
                            )
                            Text(
                                if (state.examMode) {
                                    stringResource(R.string.quiz_answered, state.answeredCount)
                                } else {
                                    stringResource(R.string.quiz_score, state.correctCount, state.answeredCount)
                                },
                                style = MaterialTheme.typography.bodySmall.tabular,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = { confirmEnd = true }) { Text(stringResource(R.string.quiz_end_early)) }
                    },
                )
                SegmentedProgress(state, Modifier.padding(horizontal = 16.dp))
            }
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = actions.onNext,
                    enabled = state.canGoNext,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                        .heightIn(min = 56.dp),
                ) {
                    Text(stringResource(if (state.isLast) R.string.quiz_finish else R.string.quiz_next))
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoLabel(state.topicNames[question.question.topicId] ?: question.question.topicId)
                if (question.question.isCalculation) InfoLabel(stringResource(R.string.quiz_calculation))
            }
            ExpandableText(
                text = question.question.prompt,
                limit = PROMPT_LIMIT,
                style = MaterialTheme.typography.titleLarge,
            )
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                question.options.forEachIndexed { i, text ->
                    OptionCard(
                        letter = LETTERS[i],
                        text = text,
                        look = optionLook(state, question, i),
                        enabled = state.examMode || state.currentAnswer == null,
                        onClick = { actions.onChoose(i) },
                    )
                }
            }
            if (state.isRevealed) Feedback(question, state.currentAnswer!!)
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text(stringResource(R.string.quiz_end_title)) },
            text = { Text(stringResource(R.string.quiz_end_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmEnd = false
                    if (state.answeredCount == 0) actions.onExit() else actions.onEndEarly()
                }) { Text(stringResource(R.string.quiz_end_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text(stringResource(R.string.quiz_keep_going)) } },
        )
    }
}

/** A non-interactive chip-like label (topic, "calculation"). */
@Composable
private fun InfoLabel(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

internal val LETTERS = listOf("A", "B", "C", "D")
private const val PROMPT_LIMIT = 400
internal const val EXPLANATION_LIMIT = 1200

internal enum class OptionLook { NORMAL, SELECTED, RIGHT, WRONG, DIMMED }

private fun optionLook(state: QuizUiState, question: SessionQuestion, index: Int): OptionLook = when {
    state.examMode -> if (state.selection == index) OptionLook.SELECTED else OptionLook.NORMAL
    state.currentAnswer == null -> OptionLook.NORMAL
    index == question.correctIndex -> OptionLook.RIGHT
    index == state.currentAnswer -> OptionLook.WRONG
    else -> OptionLook.DIMMED
}

@Composable
internal fun OptionCard(letter: String, text: String, look: OptionLook, enabled: Boolean, onClick: () -> Unit) {
    val colors = StudyTheme.colors
    val scheme = MaterialTheme.colorScheme
    val (container, content, border) = when (look) {
        OptionLook.NORMAL -> Triple(scheme.surfaceContainerLow, scheme.onSurface, scheme.outlineVariant)
        OptionLook.SELECTED -> Triple(scheme.secondaryContainer, scheme.onSecondaryContainer, scheme.primary)
        OptionLook.RIGHT -> Triple(colors.rightContainer, colors.onRightContainer, colors.right)
        OptionLook.WRONG -> Triple(colors.wrongContainer, colors.onWrongContainer, colors.wrong)
        OptionLook.DIMMED -> Triple(scheme.surfaceContainerLow, scheme.onSurface.copy(alpha = 0.6f), scheme.outlineVariant)
    }
    val description = stringResource(R.string.quiz_option, letter, MathMarkup.plain(text))
    val select = onClick
    val stateText = when (look) {
        OptionLook.RIGHT -> stringResource(R.string.quiz_option_correct)
        OptionLook.WRONG -> stringResource(R.string.quiz_option_wrong)
        OptionLook.SELECTED -> stringResource(R.string.quiz_option_selected)
        else -> null
    }
    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(if (look == OptionLook.NORMAL || look == OptionLook.DIMMED) 1.dp else 2.dp, border),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = look == OptionLook.SELECTED, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                if (stateText != null) stateDescription = stateText
                selected = look == OptionLook.SELECTED
                role = Role.RadioButton
                if (enabled) {
                    // clearAndSetSemantics drops the click action; give it back.
                    onClick(label = null) {
                        select()
                        true
                    }
                } else {
                    disabled()
                }
            },
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = border.copy(alpha = 0.15f), contentColor = content, modifier = Modifier.size(32.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(letter, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
            }
            MathText(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp).weight(1f))
        }
    }
}

/** Tutor-mode feedback: right or wrong (announced), then the model answer, explanation and say-it box. */
@Composable
private fun Feedback(question: SessionQuestion, answer: Int) {
    val right = answer == question.correctIndex
    val colors = StudyTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (right) {
                stringResource(R.string.quiz_right)
            } else {
                stringResource(R.string.quiz_wrong, LETTERS[question.correctIndex])
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (right) colors.right else colors.wrong,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        AnswerDetails(
            modelAnswer = question.question.modelAnswer,
            explanation = question.question.explanation,
            sayIt = question.question.sayIt,
            source = question.question.source,
        )
    }
}

@Composable
internal fun AnswerDetails(modelAnswer: String?, explanation: String, sayIt: String?, source: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        modelAnswer?.let {
            LabelledText(stringResource(R.string.quiz_model_answer), it)
        }
        Column {
            Label(stringResource(R.string.quiz_explanation))
            ExpandableText(explanation, EXPLANATION_LIMIT, MaterialTheme.typography.bodyLarge)
        }
        sayIt?.let { SayItBox(it) }
        source?.let {
            Text(
                stringResource(R.string.quiz_source, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LabelledText(label: String, text: String) {
    Column {
        Label(label)
        MathText(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
internal fun SayItBox(text: String) {
    val colors = StudyTheme.colors
    Surface(
        color = colors.sayItContainer,
        contentColor = colors.onSayItContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, colors.sayItBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.quiz_say_it), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            MathText(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * Text over [limit] characters (as shown, so a formula's LaTeX source doesn't count) is cut with a
 * "more" link, as long texts are allowed but discouraged. Formulas are never cut in half.
 */
@Composable
internal fun ExpandableText(text: String, limit: Int, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier) {
    val segments = remember(text) { MathMarkup.parse(text) }
    val shortened = remember(segments, limit) { truncate(segments, limit) }
    if (shortened == null) {
        MathText(segments, style = style, modifier = modifier)
        return
    }
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    Column(modifier) {
        MathText(if (expanded) segments else shortened, style = style)
        TextButton(onClick = { expanded = !expanded }) {
            Text(stringResource(if (expanded) R.string.text_less else R.string.text_more))
        }
    }
}

/** The first [limit] shown characters of [segments] plus an ellipsis, or `null` if they fit. */
internal fun truncate(segments: List<MathSegment>, limit: Int): List<MathSegment>? {
    fun shown(segment: MathSegment) = when (segment) {
        is MathSegment.Text -> segment.text.length
        is MathSegment.Math -> MathMarkup.latexToPlain(segment.latex).length
    }
    if (segments.sumOf(::shown) <= limit) return null
    val kept = ArrayList<MathSegment>()
    var used = 0
    for (segment in segments) {
        val length = shown(segment)
        if (used + length <= limit) {
            kept += segment
            used += length
            continue
        }
        if (segment is MathSegment.Text) kept += MathSegment.Text(segment.text.take(limit - used).trimEnd())
        break
    }
    // No space before the ellipsis.
    (kept.lastOrNull() as? MathSegment.Text)?.let { kept[kept.lastIndex] = MathSegment.Text(it.text.trimEnd()) }
    kept += MathSegment.Text("…")
    return kept
}

/** One segment per question, coloured as it is answered. */
@Composable
private fun SegmentedProgress(state: QuizUiState, modifier: Modifier = Modifier) {
    val colors = StudyTheme.colors
    val scheme = MaterialTheme.colorScheme
    val total = state.questions.size
    val segmentColors: List<Color> = state.questions.indices.map { i ->
        val answer = state.answers[i]
        when {
            answer == null && i == state.index -> scheme.primary.copy(alpha = 0.45f)
            answer == null -> scheme.surfaceVariant
            state.examMode -> scheme.primary
            answer == state.questions[i].correctIndex -> colors.right
            else -> colors.wrong
        }
    }
    val description = stringResource(R.string.quiz_answered, state.answeredCount)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(state.answeredCount.toFloat(), 0f..total.toFloat())
            },
    ) {
        if (total == 0) return@Canvas
        val gap = if (total <= 50) 2.dp.toPx() else 0f
        val width = (size.width - gap * (total - 1)) / total
        val radius = CornerRadius(size.height / 2, size.height / 2)
        segmentColors.forEachIndexed { i, color ->
            drawRoundRect(color, topLeft = Offset(i * (width + gap), 0f), size = Size(width, size.height), cornerRadius = if (gap > 0) radius else CornerRadius.Zero)
        }
    }
}
