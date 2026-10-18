package io.github.fianna76.speedread.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.fianna76.speedread.R
import io.github.fianna76.speedread.data.BookCollection

/** Which collection dialog is open. */
enum class CollectionDialog { NEW, ADD_TO, RENAME, DELETE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibrarySearchBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.library_close_search))
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.library_search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.library_clear_search))
                }
            }
        },
    )
}

/** Reading-state filters, then collections (and "New collection"); the shown collection has a menu. */
@Composable
internal fun LibraryFilters(
    state: LibraryUiState,
    organise: LibraryOrganiseActions,
    onNewCollection: () -> Unit,
    onRenameCollection: () -> Unit,
    onDeleteCollection: () -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(LibraryFilter.entries, key = { "filter-${it.name}" }) { filter ->
            FilterChip(
                selected = state.filter == filter,
                onClick = { organise.onFilterChange(filter) },
                label = { Text(stringResource(filter.labelRes())) },
            )
        }
        items(state.collections, key = { "collection-${it.id}" }) { collection ->
            val selected = state.selectedCollection?.id == collection.id
            FilterChip(
                selected = selected,
                onClick = { organise.onSelectCollection(if (selected) null else collection.id) },
                label = { Text("${collection.name} (${collection.bookIds.size})", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_bookmark), contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
        item(key = "new-collection") {
            FilterChip(
                selected = false,
                onClick = onNewCollection,
                label = { Text(stringResource(R.string.library_new_collection)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
    val collection = state.selectedCollection ?: return
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
    ) {
        Text(
            text = pluralStringResource(R.plurals.library_collection_header, collection.bookIds.size, collection.name, collection.bookIds.size),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.library_collection_options))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.library_rename_collection)) },
                    onClick = {
                        menuOpen = false
                        onRenameCollection()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.library_delete_collection)) },
                    onClick = {
                        menuOpen = false
                        onDeleteCollection()
                    },
                )
            }
        }
    }
}

@Composable
internal fun NoMatches(inCollection: Boolean, onShowAll: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(if (inCollection) R.string.library_collection_empty else R.string.library_no_matches),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp),
        )
        if (inCollection) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.library_collection_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 360.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onShowAll) { Text(stringResource(R.string.library_show_all)) }
    }
}

@Composable
internal fun CollectionDialogs(
    dialog: CollectionDialog?,
    state: LibraryUiState,
    organise: LibraryOrganiseActions,
    onShow: (CollectionDialog) -> Unit,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        null -> Unit
        CollectionDialog.NEW -> NameDialog(
            title = stringResource(R.string.library_new_collection),
            initial = "",
            onConfirm = { name ->
                onDismiss()
                organise.onCreateCollection(name)
            },
            onDismiss = onDismiss,
        )
        CollectionDialog.RENAME -> state.selectedCollection?.let { collection ->
            NameDialog(
                title = stringResource(R.string.library_rename_collection),
                initial = collection.name,
                onConfirm = { name ->
                    onDismiss()
                    organise.onRenameCollection(collection, name)
                },
                onDismiss = onDismiss,
            )
        }
        CollectionDialog.DELETE -> state.selectedCollection?.let { collection ->
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.library_delete_collection_title, collection.name)) },
                text = { Text(stringResource(R.string.library_delete_collection_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        onDismiss()
                        organise.onDeleteCollection(collection)
                    }) { Text(stringResource(R.string.action_delete)) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
        CollectionDialog.ADD_TO -> AddToCollectionDialog(state.collections, organise, onNew = { onShow(CollectionDialog.NEW) }, onDismiss = onDismiss)
    }
}

/** Lists the collections to add the selected books to, plus "New collection…" (which asks for a name). */
@Composable
private fun AddToCollectionDialog(collections: List<BookCollection>, organise: LibraryOrganiseActions, onNew: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_add_to_collection)) },
        text = {
            Column {
                collections.forEach { collection ->
                    ListItem(
                        headlineContent = { Text(collection.name) },
                        supportingContent = {
                            Text(pluralStringResource(R.plurals.library_books, collection.bookIds.size, collection.bookIds.size))
                        },
                        leadingContent = { Icon(painterResource(R.drawable.ic_bookmark), contentDescription = null) },
                        modifier = Modifier.clickable {
                            onDismiss()
                            organise.onAddSelectedTo(collection)
                        },
                    )
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.library_new_collection_ellipsis)) },
                    leadingContent = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onNew),
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun NameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.library_collection_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onConfirm(name) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun LibraryFilter.labelRes(): Int = when (this) {
    LibraryFilter.ALL -> R.string.library_filter_all
    LibraryFilter.READING -> R.string.library_filter_reading
    LibraryFilter.NOT_STARTED -> R.string.library_filter_not_started
    LibraryFilter.FINISHED -> R.string.library_filter_finished
}
