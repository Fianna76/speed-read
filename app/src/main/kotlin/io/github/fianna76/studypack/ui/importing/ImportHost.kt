package io.github.fianna76.studypack.ui.importing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fianna76.studypack.R
import io.github.fianna76.studypack.StudyPackApplication
import io.github.fianna76.studypack.importing.ImportCoordinator
import io.github.fianna76.studypack.importing.ImportDecision
import io.github.fianna76.studypack.importing.ImportOutcome
import io.github.fianna76.studypack.importing.ImportReport
import io.github.fianna76.studypack.importing.ModuleChoice

/** Shows import progress, the questions an import asks, and the summary when it finishes. */
@Composable
fun ImportHost() {
    val context = LocalContext.current
    val coordinator = remember { (context.applicationContext as StudyPackApplication).container.importCoordinator }
    ImportHost(coordinator)
}

@Composable
fun ImportHost(coordinator: ImportCoordinator) {
    val state by coordinator.state.collectAsStateWithLifecycle()
    val decision = state.decision

    if (state.running && decision == null) {
        Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .padding(top = 72.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                    Text(stringResource(R.string.import_running), modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }

    when (decision) {
        is ImportDecision.ReplacePack -> ReplaceDialog(decision, onAnswer = { coordinator.replace(decision, it) })
        is ImportDecision.ChooseModule -> ChooseModuleDialog(decision, onAnswer = { coordinator.chooseModule(decision, it) })
        null -> if (!state.running && state.reports.isNotEmpty()) ReportDialog(state.reports, onDismiss = coordinator::dismissReports)
    }
}

@Composable
private fun ReplaceDialog(decision: ImportDecision.ReplacePack, onAnswer: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAnswer(false) },
        title = { Text(stringResource(R.string.import_replace_title, decision.title)) },
        text = {
            Text(
                if (decision.isOlder) {
                    stringResource(R.string.import_replace_older, decision.currentVersion, decision.newVersion)
                } else {
                    stringResource(R.string.import_replace_same, decision.currentVersion)
                },
            )
        },
        confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text(stringResource(R.string.import_replace_confirm)) } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text(stringResource(R.string.import_keep)) } },
    )
}

@Composable
private fun ChooseModuleDialog(decision: ImportDecision.ChooseModule, onAnswer: (ModuleChoice?) -> Unit) {
    // Index into the modules, or -1 for a new module (the only option when there are none).
    var selected by rememberSaveable(decision.fileName) { mutableIntStateOf(if (decision.modules.isEmpty()) -1 else 0) }
    var code by rememberSaveable(decision.fileName) { mutableStateOf("") }
    var name by rememberSaveable(decision.fileName) { mutableStateOf("") }
    val choice = if (selected >= 0) decision.modules.getOrNull(selected) else code.trim().takeIf { it.isNotEmpty() }?.let { ModuleChoice(it, name) }

    AlertDialog(
        onDismissRequest = { onAnswer(null) },
        title = { Text(stringResource(R.string.import_choose_module_title, decision.fileName)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.import_choose_module_body), style = MaterialTheme.typography.bodyMedium)
                Column(Modifier.selectableGroup().padding(top = 8.dp)) {
                    decision.modules.forEachIndexed { i, module ->
                        ChoiceRow(
                            label = module.name?.let { "${module.code} · $it" } ?: module.code,
                            selected = selected == i,
                            onSelect = { selected = i },
                        )
                    }
                    ChoiceRow(stringResource(R.string.import_new_module), selected = selected == -1, onSelect = { selected = -1 })
                }
                if (selected == -1) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.take(MAX_CODE_LENGTH) },
                        label = { Text(stringResource(R.string.import_new_module_code)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                        label = { Text(stringResource(R.string.import_new_module_name)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAnswer(choice) }, enabled = choice != null) { Text(stringResource(R.string.import_add)) }
        },
        dismissButton = { TextButton(onClick = { onAnswer(null) }) { Text(stringResource(R.string.cancel)) } },
    )
}

private const val MAX_CODE_LENGTH = 20
private const val MAX_NAME_LENGTH = 80

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun ReportDialog(reports: List<ImportReport>, onDismiss: () -> Unit) {
    val allFailed = reports.all { it.outcome is ImportOutcome.Failed }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (allFailed) R.string.import_report_title_failed else R.string.import_report_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                reports.forEach { ReportItem(it) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}

@Composable
private fun ReportItem(report: ImportReport) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(report.fileName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        when (val outcome = report.outcome) {
            is ImportOutcome.PackImported -> {
                val questions = pluralStringResource(R.plurals.module_questions, outcome.questionCount, outcome.questionCount)
                val topics = pluralStringResource(R.plurals.setup_topic_count, outcome.topicCount, outcome.topicCount)
                var summary = if (outcome.replacedVersion != null) {
                    stringResource(R.string.import_pack_summary_replaced, questions, topics, outcome.version)
                } else {
                    stringResource(R.string.import_pack_summary, questions, topics)
                }
                if (outcome.warnings.isNotEmpty()) {
                    summary += " (" + pluralStringResource(R.plurals.import_warning_count, outcome.warnings.size, outcome.warnings.size) + ")"
                }
                Text(summary, style = MaterialTheme.typography.bodyMedium)
                outcome.warnings.forEach { Bullet(it, MaterialTheme.colorScheme.tertiary) }
                outcome.infos.forEach { Bullet(it, MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            is ImportOutcome.NotesImported -> Text(
                stringResource(
                    if (outcome.replaced) R.string.import_notes_summary_replaced else R.string.import_notes_summary,
                    outcome.moduleCode,
                    pluralStringResource(R.plurals.note_pages, outcome.pageCount, outcome.pageCount),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            is ImportOutcome.KeptExisting -> Text(stringResource(R.string.import_skipped_kept, outcome.version), style = MaterialTheme.typography.bodyMedium)
            ImportOutcome.Cancelled -> Text(stringResource(R.string.import_skipped_cancelled), style = MaterialTheme.typography.bodyMedium)
            is ImportOutcome.Failed -> outcome.messages.forEach { Bullet(it, MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun Bullet(text: String, color: androidx.compose.ui.graphics.Color) {
    Row {
        Text("•", color = color, modifier = Modifier.padding(end = 8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}
