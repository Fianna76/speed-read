package io.github.fianna76.studypack.ui.module

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fianna76.studypack.R
import io.github.fianna76.studypack.StudyPackApplication
import io.github.fianna76.studypack.data.NoteFile
import io.github.fianna76.studypack.data.PackSummary
import io.github.fianna76.studypack.ui.components.BackButton
import io.github.fianna76.studypack.ui.components.ConfirmDelete
import io.github.fianna76.studypack.ui.components.ConfirmDeleteModule
import io.github.fianna76.studypack.ui.components.SectionHeader
import io.github.fianna76.studypack.ui.library.IMPORT_MIME_TYPES
import io.github.fianna76.studypack.ui.theme.tabular

@Composable
fun ModuleScreen(
    moduleCode: String,
    onBack: () -> Unit,
    onQuiz: (packId: String?) -> Unit,
    onOpenNote: (Long) -> Unit,
    onSpeedRead: (noteId: Long, page: Int?) -> Unit,
    viewModel: ModuleViewModel = viewModel(factory = ModuleViewModel.factory(moduleCode)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { viewModel.importHere(it) }
    // Leave once the last pack and notes are deleted.
    LaunchedEffect(state.isGone, state.isLoading) {
        if (!state.isLoading && state.isGone) onBack()
    }
    ModuleContent(
        state = state,
        actions = ModuleActions(
            onBack = onBack,
            onQuiz = onQuiz,
            onOpenNote = onOpenNote,
            // Resume where speed reading stopped.
            onSpeedRead = { note -> onSpeedRead(note.id, null) },
            onOpenExternally = { note ->
                val files = (context.applicationContext as StudyPackApplication).container.files
                openPdfExternally(context, files.file(note.internalPath), note.displayTitle)
            },
            onDeletePack = viewModel::deletePack,
            onDeleteNote = viewModel::deleteNote,
            onDeleteModule = viewModel::deleteModule,
            onImport = { picker.launch(IMPORT_MIME_TYPES) },
        ),
    )
}

class ModuleActions(
    val onBack: () -> Unit = {},
    val onQuiz: (packId: String?) -> Unit = {},
    val onOpenNote: (Long) -> Unit = {},
    val onSpeedRead: (NoteFile) -> Unit = {},
    val onOpenExternally: (NoteFile) -> Unit = {},
    val onDeletePack: (String) -> Unit = {},
    val onDeleteNote: (Long) -> Unit = {},
    val onDeleteModule: () -> Unit = {},
    val onImport: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleContent(state: ModuleUiState, actions: ModuleActions) {
    var deletingPack by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingNote by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingModule by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(actions.onBack) },
                title = {
                    Column {
                        Text(state.code, style = MaterialTheme.typography.titleLarge)
                        state.name?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.module_import_here)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_upload_file), contentDescription = null) },
                            onClick = {
                                menu = false
                                actions.onImport()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete_module)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                            onClick = {
                                menu = false
                                deletingModule = true
                            },
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (state.packs.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = { actions.onQuiz(null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp)
                            .heightIn(min = 56.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_quiz), contentDescription = null)
                        Text(
                            stringResource(if (state.packs.size > 1) R.string.module_quiz_all else R.string.module_quiz),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SectionHeader(stringResource(R.string.module_notes_section)) }
            if (state.notes.isEmpty()) {
                item { HintText(stringResource(R.string.module_no_notes)) }
            }
            items(state.notes, key = { "note-${it.id}" }) { note ->
                NoteCard(
                    note = note,
                    onOpen = { actions.onOpenNote(note.id) },
                    onSpeedRead = { actions.onSpeedRead(note) },
                    onOpenExternally = { actions.onOpenExternally(note) },
                    onDelete = { deletingNote = note.id },
                )
            }
            item { SectionHeader(stringResource(R.string.module_packs_section), Modifier.padding(top = 8.dp)) }
            if (state.packs.isEmpty()) {
                item { HintText(stringResource(R.string.module_no_packs)) }
            }
            items(state.packs, key = { "pack-${it.id}" }) { pack ->
                PackCard(pack = pack, onQuiz = { actions.onQuiz(pack.id) }, onDelete = { deletingPack = pack.id })
            }
        }
    }

    state.packs.firstOrNull { it.id == deletingPack }?.let { pack ->
        ConfirmDelete(
            title = stringResource(R.string.delete_pack_title, pack.title),
            body = stringResource(R.string.delete_pack_body),
            onConfirm = {
                actions.onDeletePack(pack.id)
                deletingPack = null
            },
            onDismiss = { deletingPack = null },
        )
    }
    if (deletingModule) {
        ConfirmDeleteModule(
            moduleCode = state.code,
            packCount = state.packs.size,
            noteCount = state.notes.size,
            onConfirm = {
                deletingModule = false
                actions.onDeleteModule()
            },
            onDismiss = { deletingModule = false },
        )
    }
    state.notes.firstOrNull { it.id == deletingNote }?.let { note ->
        ConfirmDelete(
            title = stringResource(R.string.delete_note_title, note.displayTitle),
            body = stringResource(R.string.delete_note_body),
            onConfirm = {
                actions.onDeleteNote(note.id)
                deletingNote = null
            },
            onDismiss = { deletingNote = null },
        )
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    note: NoteFile,
    onOpen: () -> Unit,
    onSpeedRead: () -> Unit,
    onOpenExternally: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val moreLabel = stringResource(R.string.more_options_for, note.displayTitle)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(onClick = onOpen, onLongClick = { menu = true }, onLongClickLabel = moreLabel)
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_description), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                Text(note.displayTitle, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (note.lastPage > 0) {
                        stringResource(R.string.note_page_progress, note.lastPage + 1, note.pageCount)
                    } else {
                        pluralStringResource(R.plurals.note_pages, note.pageCount, note.pageCount)
                    },
                    style = MaterialTheme.typography.bodyMedium.tabular,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(painterResource(R.drawable.ic_more_vert), contentDescription = moreLabel)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    MenuItem(R.string.note_speed_read, R.drawable.ic_bolt) { menu = false; onSpeedRead() }
                    MenuItem(R.string.note_open_external, R.drawable.ic_open_in_new) { menu = false; onOpenExternally() }
                    MenuItem(R.string.delete, R.drawable.ic_delete) { menu = false; onDelete() }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PackCard(pack: PackSummary, onQuiz: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val moreLabel = stringResource(R.string.more_options_for, pack.title)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(onClick = onQuiz, onLongClick = { menu = true }, onLongClickLabel = moreLabel)
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_quiz), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(pack.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.pack_version, pack.version) + stringResource(R.string.module_summary_separator) +
                        pluralStringResource(R.plurals.module_questions, pack.questionCount, pack.questionCount),
                    style = MaterialTheme.typography.bodyMedium.tabular,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (pack.bestPercent != null && pack.lastPercent != null) {
                    Text(
                        stringResource(R.string.pack_scores, pack.bestPercent, pack.lastPercent),
                        style = MaterialTheme.typography.bodyMedium.tabular,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(painterResource(R.drawable.ic_more_vert), contentDescription = moreLabel)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    MenuItem(R.string.pack_quiz_this, R.drawable.ic_quiz) { menu = false; onQuiz() }
                    MenuItem(R.string.delete, R.drawable.ic_delete) { menu = false; onDelete() }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(label: Int, icon: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
        onClick = onClick,
    )
}
