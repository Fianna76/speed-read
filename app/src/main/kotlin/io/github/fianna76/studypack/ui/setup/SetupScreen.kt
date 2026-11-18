package io.github.fianna76.studypack.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fianna76.studypack.R
import io.github.fianna76.studypack.quiz.QuizLength
import io.github.fianna76.studypack.ui.components.BackButton
import io.github.fianna76.studypack.ui.components.SectionHeader
import io.github.fianna76.studypack.ui.navigation.QuizRoute
import io.github.fianna76.studypack.ui.theme.tabular

@Composable
fun SetupScreen(
    moduleCode: String,
    packId: String?,
    onBack: () -> Unit,
    onStart: (QuizRoute) -> Unit,
    viewModel: SetupViewModel = viewModel(factory = SetupViewModel.factory(moduleCode, packId)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SetupContent(
        state = state,
        subtitle = state.packTitle ?: moduleCode,
        actions = SetupActions(
            onBack = onBack,
            onLength = viewModel::selectLength,
            onToggleTopic = viewModel::toggleTopic,
            onSelectAll = viewModel::selectAllTopics,
            onDemoAsked = viewModel::setDemoAskedOnly,
            onExamMode = viewModel::setExamMode,
            onStart = { viewModel.quizRoute()?.let(onStart) },
        ),
    )
}

class SetupActions(
    val onBack: () -> Unit = {},
    val onLength: (QuizLength) -> Unit = {},
    val onToggleTopic: (String) -> Unit = {},
    val onSelectAll: () -> Unit = {},
    val onDemoAsked: (Boolean) -> Unit = {},
    val onExamMode: (Boolean) -> Unit = {},
    val onStart: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SetupContent(state: SetupUiState, subtitle: String, actions: SetupActions) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(actions.onBack) },
                title = {
                    Column {
                        Text(stringResource(R.string.setup_title))
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        if (state.sessionSize == 0 && !state.isLoading) {
                            stringResource(R.string.setup_no_questions)
                        } else {
                            stringResource(
                                R.string.setup_summary,
                                pluralStringResource(R.plurals.module_questions, state.sessionSize, state.sessionSize),
                                pluralStringResource(R.plurals.setup_topic_count, state.sessionTopics, state.sessionTopics),
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium.tabular,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = actions.onStart,
                        enabled = state.canStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null)
                        Text(stringResource(R.string.setup_start), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            SectionHeader(stringResource(R.string.setup_length))
            Column(Modifier.selectableGroup()) {
                state.lengths.forEach { option ->
                    LengthRow(option, selected = option.length == state.length, onSelect = { actions.onLength(option.length) })
                }
            }
            if (!state.weakSpotsAvailable) {
                Text(
                    stringResource(R.string.weak_spots_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 64.dp, end = 16.dp),
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeader(stringResource(R.string.setup_topics), Modifier.weight(1f))
                TextButton(onClick = actions.onSelectAll, enabled = !state.allTopicsSelected) {
                    Text(stringResource(R.string.setup_select_all))
                }
            }
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.topics.forEach { topic ->
                    val selected = topic.id in state.selectedTopics
                    FilterChip(
                        selected = selected,
                        onClick = { actions.onToggleTopic(topic.id) },
                        label = { Text(stringResource(R.string.setup_topic_chip, topic.name, topic.count)) },
                        leadingIcon = if (selected) {
                            { Icon(painterResource(R.drawable.ic_check), contentDescription = null) }
                        } else {
                            null
                        },
                    )
                }
                if (state.demoAskedAvailable) {
                    FilterChip(
                        selected = state.demoAskedOnly,
                        onClick = { actions.onDemoAsked(!state.demoAskedOnly) },
                        label = { Text(stringResource(R.string.setup_demo_asked)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_flag), contentDescription = null) },
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .toggleable(value = state.examMode, role = Role.Switch, onValueChange = actions.onExamMode)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.setup_exam_mode), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(if (state.examMode) R.string.setup_exam_mode_on else R.string.setup_exam_mode_off),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.examMode, onCheckedChange = null, modifier = Modifier.padding(start = 16.dp))
            }
        }
    }
}

@Composable
private fun LengthRow(option: LengthOption, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, enabled = option.enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = option.enabled)
        Text(
            lengthLabel(option.length),
            style = MaterialTheme.typography.bodyLarge,
            color = if (option.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        )
        Text(
            option.length.questionCount?.takeIf { option.length != QuizLength.WEAK_SPOTS }?.let { stringResource(R.string.length_count, it) }
                ?: if (option.length == QuizLength.EVERYTHING) stringResource(R.string.length_all) else "",
            style = MaterialTheme.typography.bodyMedium.tabular,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun lengthLabel(length: QuizLength): String = stringResource(
    when (length) {
        QuizLength.WARM_UP -> R.string.length_warm_up
        QuizLength.BENCH_DEMO -> R.string.length_bench_demo
        QuizLength.FULL_MOCK -> R.string.length_full_mock
        QuizLength.EVERYTHING -> R.string.length_everything
        QuizLength.WEAK_SPOTS -> R.string.length_weak_spots
    },
)
