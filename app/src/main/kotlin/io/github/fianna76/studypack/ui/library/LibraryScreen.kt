package io.github.fianna76.studypack.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fianna76.studypack.R
import io.github.fianna76.studypack.data.ModuleSummary
import io.github.fianna76.studypack.ui.components.EmptyState
import io.github.fianna76.studypack.ui.theme.tabular

/** MIME types offered by the picker. Cloud drives often label .json files as plain text or binary. */
internal val IMPORT_MIME_TYPES = arrayOf("application/json", "application/pdf", "application/octet-stream", "text/plain")

@Composable
fun LibraryScreen(
    onOpenModule: (String) -> Unit,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.import(uris)
    }
    LibraryContent(
        state = state,
        onOpenModule = onOpenModule,
        onImport = { picker.launch(IMPORT_MIME_TYPES) },
        onLoadSample = viewModel::loadSample,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryContent(
    state: LibraryUiState,
    onOpenModule: (String) -> Unit,
    onImport: () -> Unit,
    onLoadSample: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.library_load_sample)) },
                            onClick = {
                                menu = false
                                onLoadSample()
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onImport,
                icon = { Icon(painterResource(R.drawable.ic_upload_file), contentDescription = null) },
                text = { Text(stringResource(R.string.library_import)) },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.modules.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = R.drawable.ic_quiz,
                    title = stringResource(R.string.library_empty_title),
                    body = stringResource(R.string.library_empty_body),
                ) {
                    FilledTonalButton(onClick = onLoadSample) { Text(stringResource(R.string.library_load_sample)) }
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.modules, key = { it.code }) { module ->
                    ModuleCard(module, onClick = { onOpenModule(module.code) })
                }
            }
        }
    }
}

@Composable
private fun ModuleCard(module: ModuleSummary, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(module.code, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                module.name ?: module.code,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                moduleCounts(module),
                style = MaterialTheme.typography.bodyMedium.tabular,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            module.lastPercent?.let {
                Text(
                    stringResource(R.string.last_score, it),
                    style = MaterialTheme.typography.bodyMedium.tabular,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun moduleCounts(module: ModuleSummary): String {
    val separator = stringResource(R.string.module_summary_separator)
    val parts = buildList {
        if (module.packCount > 0) add(pluralStringResource(R.plurals.module_packs, module.packCount, module.packCount))
        if (module.noteCount > 0) add(pluralStringResource(R.plurals.module_notes, module.noteCount, module.noteCount))
        if (module.questionCount > 0) add(pluralStringResource(R.plurals.module_questions, module.questionCount, module.questionCount))
    }
    return parts.joinToString(separator)
}

