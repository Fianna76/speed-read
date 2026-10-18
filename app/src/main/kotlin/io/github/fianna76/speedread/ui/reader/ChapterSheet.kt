package io.github.fianna76.speedread.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.fianna76.speedread.R
import io.github.fianna76.speedread.data.Bookmark
import io.github.fianna76.speedread.data.Chapter
import io.github.fianna76.speedread.rsvp.Pacing
import io.github.fianna76.speedread.rsvp.PlaybackSettings
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterSheet(
    chapters: List<Chapter>,
    currentIndex: Int,
    settings: PlaybackSettings,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    bookmarks: List<Bookmark> = emptyList(),
    onSelectBookmark: (Bookmark) -> Unit = {},
    onDeleteBookmark: (Bookmark) -> Unit = {},
) {
    var showBookmarks by rememberSaveable { mutableStateOf(false) }
    val numberFormat = remember { NumberFormat.getIntegerInstance() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 2).coerceAtLeast(0))
    // Fully expanded: a partially expanded sheet would push the end of the list under the
    // navigation bar.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        PrimaryTabRow(selectedTabIndex = if (showBookmarks) 1 else 0) {
            Tab(
                selected = !showBookmarks,
                onClick = { showBookmarks = false },
                text = { Text(stringResource(R.string.reader_chapters)) },
            )
            Tab(
                selected = showBookmarks,
                onClick = { showBookmarks = true },
                text = { Text(stringResource(R.string.reader_bookmarks, bookmarks.size)) },
            )
        }
        if (showBookmarks) {
            BookmarkList(chapters, bookmarks, onSelectBookmark, onDeleteBookmark)
            return@ModalBottomSheet
        }
        LazyColumn(state = listState) {
            itemsIndexed(chapters, key = { _, chapter -> chapter.index }) { position, chapter ->
                val isCurrent = position == currentIndex
                val words = pluralStringResource(
                    R.plurals.reader_word_count,
                    chapter.wordCount,
                    numberFormat.format(chapter.wordCount),
                )
                val duration = formatDuration(Pacing.estimateMillis(chapter.wordCount, settings))
                    ?: stringResource(R.string.reader_less_than_a_minute)
                ListItem(
                    headlineContent = {
                        Text(chapter.displayTitle(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = { Text(stringResource(R.string.reader_chapter_details, words, duration)) },
                    trailingContent = if (isCurrent) {
                        { Icon(painterResource(R.drawable.ic_check), contentDescription = null) }
                    } else {
                        null
                    },
                    colors = if (isCurrent) {
                        ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    } else {
                        ListItemDefaults.colors()
                    },
                    modifier = Modifier
                        .semantics { selected = isCurrent }
                        .clickable { onSelect(position) },
                )
            }
        }
    }
}

@Composable
private fun BookmarkList(
    chapters: List<Chapter>,
    bookmarks: List<Bookmark>,
    onSelect: (Bookmark) -> Unit,
    onDelete: (Bookmark) -> Unit,
) {
    if (bookmarks.isEmpty()) {
        Text(
            text = stringResource(R.string.reader_bookmarks_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
        return
    }
    LazyColumn {
        items(bookmarks, key = { it.id }) { bookmark ->
            val chapter = chapters.getOrNull(bookmark.position.chapterIndex)
            ListItem(
                headlineContent = { Text(bookmark.snippet + "\u2026", maxLines = 2, overflow = TextOverflow.Ellipsis) },
                supportingContent = chapter?.let { { Text(it.displayTitle(), maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                leadingContent = { Icon(painterResource(R.drawable.ic_bookmark_filled), contentDescription = null) },
                trailingContent = {
                    IconButton(onClick = { onDelete(bookmark) }) {
                        Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.reader_delete_bookmark))
                    }
                },
                modifier = Modifier.clickable { onSelect(bookmark) },
            )
        }
    }
}

/** The chapter's own title, else its PDF pages, else a numbered fallback for untitled sections. */
@Composable
fun Chapter.displayTitle(): String {
    title?.let { return it }
    val pages = pageRange ?: return stringResource(R.string.reader_section, index + 1)
    return if (pages.first == pages.last) {
        stringResource(R.string.reader_page, pages.first + 1)
    } else {
        stringResource(R.string.reader_pages, pages.first + 1, pages.last + 1)
    }
}
