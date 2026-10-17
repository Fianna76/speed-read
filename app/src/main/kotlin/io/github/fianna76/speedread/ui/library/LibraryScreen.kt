package io.github.fianna76.speedread.ui.library

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fianna76.speedread.R
import io.github.fianna76.speedread.data.Book
import io.github.fianna76.speedread.data.ReadingPosition
import io.github.fianna76.speedread.data.importing.ImportError
import io.github.fianna76.speedread.data.importing.ImportResult
import io.github.fianna76.speedread.data.settings.LibrarySort
import io.github.fianna76.speedread.ui.theme.SpeedReadTheme

/**
 * MIME types offered in the system file picker. Some providers label EPUBs as generic binaries;
 * the importer identifies the real format from the file's content.
 */
private val BOOK_MIME_TYPES = arrayOf("application/epub+zip", "application/pdf", "application/octet-stream")

@Composable
fun LibraryScreen(
    onOpenBook: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pickBooks = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        viewModel.importBooks(uris)
    }
    LibraryContent(
        state = state,
        onImport = { pickBooks.launch(BOOK_MIME_TYPES) },
        onOpenBook = onOpenBook,
        onOpenSettings = onOpenSettings,
        onSortChange = viewModel::setSort,
        onToggleSelection = viewModel::toggleSelection,
        onSelectAll = viewModel::selectAll,
        onClearSelection = viewModel::clearSelection,
        onDeleteSelected = viewModel::deleteSelected,
        onMessageShown = viewModel::onMessageShown,
        bottomBar = bottomBar,
    )
}

@Composable
fun LibraryContent(
    state: LibraryUiState,
    onImport: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onMessageShown: (Long) -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = state.isSelecting, onBack = onClearSelection)

    state.messages.firstOrNull()?.let { message ->
        val text = message.text()
        val bookToOpen = message.bookToOpen()
        val actionLabel = if (bookToOpen != null) stringResource(R.string.action_read) else null
        LaunchedEffect(message.id) {
            val result = snackbarHostState.showSnackbar(text, actionLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed && bookToOpen != null) onOpenBook(bookToOpen)
            onMessageShown(message.id)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (state.isSelecting) {
                SelectionTopBar(
                    count = state.selection.size,
                    onClear = onClearSelection,
                    onSelectAll = onSelectAll,
                    onDelete = { confirmDelete = true },
                )
            } else {
                LibraryTopBar(sort = state.sort, onSortChange = onSortChange, onOpenSettings = onOpenSettings)
            }
        },
        floatingActionButton = {
            if (!state.isSelecting && state.books.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onImport,
                    icon = { Icon(painterResource(R.drawable.ic_library_add), contentDescription = null) },
                    text = { Text(stringResource(R.string.library_import)) },
                )
            }
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.importsInProgress > 0) ImportProgressBanner(state.importsInProgress)
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.books.isEmpty() -> EmptyLibrary(onImport = onImport, modifier = Modifier.fillMaxSize())
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.books, key = { it.id }) { book ->
                        BookCard(
                            book = book,
                            selectionMode = state.isSelecting,
                            selected = book.id in state.selection,
                            onClick = { if (state.isSelecting) onToggleSelection(book.id) else onOpenBook(book.id) },
                            onLongClick = { onToggleSelection(book.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        val count = state.selection.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(pluralStringResource(R.plurals.library_delete_title, count, count)) },
            text = { Text(stringResource(R.string.library_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDeleteSelected()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTopBar(sort: LibrarySort, onSortChange: (LibrarySort) -> Unit, onOpenSettings: () -> Unit) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(stringResource(R.string.library_title)) },
        actions = {
            Box {
                IconButton(onClick = { sortMenuOpen = true }) {
                    Icon(painterResource(R.drawable.ic_sort), contentDescription = stringResource(R.string.library_sort))
                }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    LibrarySort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.labelRes())) },
                            leadingIcon = { RadioButton(selected = option == sort, onClick = null) },
                            onClick = {
                                sortMenuOpen = false
                                onSortChange(option)
                            },
                        )
                    }
                }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.action_settings))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(count: Int, onClear: () -> Unit, onSelectAll: () -> Unit, onDelete: () -> Unit) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClear) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.library_clear_selection))
            }
        },
        title = { Text(pluralStringResource(R.plurals.library_selected, count, count)) },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(painterResource(R.drawable.ic_select_all), contentDescription = stringResource(R.string.library_select_all))
            }
            IconButton(onClick = onDelete) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.action_delete))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    )
}

@Composable
private fun ImportProgressBanner(count: Int) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = pluralStringResource(R.plurals.library_importing, count, count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
private fun EmptyLibrary(onImport: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_menu_book),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.library_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.library_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onImport) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_library_add), contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.library_import))
            }
        }
    }
}

private fun LibrarySort.labelRes(): Int = when (this) {
    LibrarySort.RECENT -> R.string.sort_recent
    LibrarySort.TITLE -> R.string.sort_title
    LibrarySort.AUTHOR -> R.string.sort_author
    LibrarySort.ADDED -> R.string.sort_added
}

@Composable
private fun LibraryMessage.text(): String = when (this) {
    is LibraryMessage.Deleted -> pluralStringResource(R.plurals.library_deleted, count, count)
    is LibraryMessage.Import -> when (val result = result) {
        is ImportResult.Imported -> stringResource(R.string.import_success, result.title)
        is ImportResult.AlreadyInLibrary -> stringResource(R.string.import_duplicate, result.title)
        is ImportResult.Failed -> {
            val name = result.fileName ?: stringResource(R.string.import_unnamed_file)
            when (result.reason) {
                ImportError.INVALID_FILE -> stringResource(R.string.import_failed_invalid, name)
                ImportError.PASSWORD_PROTECTED -> stringResource(R.string.import_failed_password, name)
                ImportError.DRM_PROTECTED -> stringResource(R.string.import_failed_drm, name)
                ImportError.NO_TEXT -> stringResource(R.string.import_failed_no_text, name)
                ImportError.TOO_LARGE -> stringResource(R.string.import_failed_too_large, name)
                ImportError.UNREADABLE -> stringResource(R.string.import_failed_unreadable, name)
            }
        }
    }
}

private fun LibraryMessage.bookToOpen(): Long? = when (val message = this) {
    is LibraryMessage.Import -> when (val result = message.result) {
        is ImportResult.Imported -> result.bookId
        is ImportResult.AlreadyInLibrary -> result.bookId
        is ImportResult.Failed -> null
    }
    is LibraryMessage.Deleted -> null
}

@Preview(showBackground = true, widthDp = 400, heightDp = 720)
@Composable
private fun LibraryPreview() {
    SpeedReadTheme(dynamicColor = false) {
        LibraryContent(
            state = LibraryUiState(
                isLoading = false,
                books = listOf(
                    previewBook(1, "Pride and Prejudice", "Jane Austen", wordsRead = 52_000),
                    previewBook(2, "Moby-Dick; or, The Whale", "Herman Melville", wordsRead = 0),
                    previewBook(3, "The Time Machine", "H. G. Wells", wordsRead = 32_000),
                ),
                selection = setOf(2),
            ),
            onImport = {}, onOpenBook = {}, onOpenSettings = {}, onSortChange = {}, onToggleSelection = {},
            onSelectAll = {}, onClearSelection = {}, onDeleteSelected = {}, onMessageShown = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 720)
@Composable
private fun EmptyLibraryPreview() {
    SpeedReadTheme(dynamicColor = false) {
        LibraryContent(
            state = LibraryUiState(isLoading = false),
            onImport = {}, onOpenBook = {}, onOpenSettings = {}, onSortChange = {}, onToggleSelection = {},
            onSelectAll = {}, onClearSelection = {}, onDeleteSelected = {}, onMessageShown = {},
        )
    }
}

internal fun previewBook(id: Long, title: String, author: String?, wordsRead: Int, totalWords: Int = 120_000) = Book(
    id = id,
    title = title,
    author = author,
    coverPath = null,
    totalWords = totalWords,
    addedAt = 0,
    lastOpenedAt = null,
    position = ReadingPosition(0, 0),
    wordsRead = wordsRead,
)
