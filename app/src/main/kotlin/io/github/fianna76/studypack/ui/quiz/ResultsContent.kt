package io.github.fianna76.studypack.ui.quiz

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.fianna76.studypack.R
import io.github.fianna76.studypack.quiz.QuizScore
import io.github.fianna76.studypack.quiz.TopicScore
import io.github.fianna76.studypack.quiz.Verdict
import io.github.fianna76.studypack.ui.components.MathText
import io.github.fianna76.studypack.ui.components.SectionHeader
import io.github.fianna76.studypack.ui.theme.StudyTheme
import io.github.fianna76.studypack.ui.theme.tabular

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ResultsContent(state: QuizUiState, actions: QuizActions) {
    BackHandler(onBack = actions.onExit)
    val score = state.score ?: return
    val missed = state.missed
    var showCorrect by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.results_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onExit) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.close))
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = actions.onNewQuiz,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp),
                    ) { Text(stringResource(R.string.results_new_quiz)) }
                    if (missed.isNotEmpty()) {
                        Button(
                            onClick = actions.onRetryMissed,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 56.dp),
                        ) { Text(stringResource(R.string.results_retry_missed)) }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { ScoreHeader(score) }
            if (score.perTopic.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.results_by_topic)) }
                items(score.perTopic, key = { "topic-${it.topicId}" }) { TopicBar(it) }
            }
            item { SectionHeader(stringResource(R.string.results_missed), Modifier.padding(top = 8.dp)) }
            if (missed.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.results_no_misses),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            items(missed, key = { "missed-$it" }) { index ->
                ReviewCard(state, index, expandedDetails = true)
            }
            val right = state.right
            if (right.isNotEmpty()) {
                item {
                    TextButton(onClick = { showCorrect = !showCorrect }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(stringResource(R.string.results_correct_section, right.size))
                        Icon(
                            painterResource(if (showCorrect) R.drawable.ic_close else R.drawable.ic_add),
                            contentDescription = null,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                if (showCorrect) {
                    items(right, key = { "right-$it" }) { index -> ReviewCard(state, index, expandedDetails = false) }
                }
            }
        }
    }
}

@Composable
private fun ScoreHeader(score: QuizScore) {
    val colors = StudyTheme.colors
    val verdictColor = when (score.verdict) {
        Verdict.DEMO_READY -> colors.right
        Verdict.NEARLY_THERE -> colors.sayItBorder
        Verdict.REVISE -> colors.wrong
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.percent, score.percent),
            style = MaterialTheme.typography.displayMedium.tabular,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(
                when (score.verdict) {
                    Verdict.DEMO_READY -> R.string.verdict_demo_ready
                    Verdict.NEARLY_THERE -> R.string.verdict_nearly_there
                    Verdict.REVISE -> R.string.verdict_revise
                },
            ),
            style = MaterialTheme.typography.titleLarge,
            color = verdictColor,
        )
        Text(
            stringResource(R.string.results_score, score.correct, score.answered),
            style = MaterialTheme.typography.bodyLarge.tabular,
            textAlign = TextAlign.Center,
        )
        if (score.answered < score.total) {
            Text(
                stringResource(R.string.results_answered_of, score.answered, score.total),
                style = MaterialTheme.typography.bodyMedium.tabular,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TopicBar(topic: TopicScore) {
    val colors = StudyTheme.colors
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(topic.topicName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.results_topic_score, topic.correct, topic.answered),
                style = MaterialTheme.typography.bodyMedium.tabular,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { topic.fraction },
            color = when {
                topic.fraction >= 0.85f -> colors.right
                topic.fraction >= 0.65f -> colors.sayItBorder
                else -> colors.wrong
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun ReviewCard(state: QuizUiState, index: Int, expandedDetails: Boolean) {
    val asked = state.questions[index]
    val answer = state.answers[index] ?: return
    val colors = StudyTheme.colors
    val right = answer == asked.correctIndex
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                state.topicNames[asked.question.topicId] ?: asked.question.topicId,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            MathText(asked.question.prompt, style = MaterialTheme.typography.titleMedium)
            if (!right) {
                MathText(
                    stringResource(R.string.results_your_answer, "${LETTERS[answer]}. ${asked.options[answer]}"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.wrong,
                )
            }
            MathText(
                stringResource(R.string.results_right_answer, "${LETTERS[asked.correctIndex]}. ${asked.options[asked.correctIndex]}"),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.right,
            )
            if (expandedDetails) {
                ExpandableText(asked.question.explanation, EXPLANATION_LIMIT, MaterialTheme.typography.bodyMedium)
                asked.question.sayIt?.let { SayItBox(it) }
            }
        }
    }
}
