package io.github.fianna76.studypack.ui.rsvp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.fianna76.speedread.rsvp.Pacing
import io.github.fianna76.speedread.rsvp.PlaybackSettings
import io.github.fianna76.studypack.R
import io.github.fianna76.studypack.data.pdf.SectionKind
import java.text.NumberFormat

/**
 * The notes' sections: pick one to read from its start. The title page, contents and sources are
 * listed too (dimmed, "Skipped when reading"), so they can still be read on purpose.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionSheet(
    chapters: List<ChapterInfo>,
    currentIndex: Int,
    settings: PlaybackSettings,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val numberFormat = remember { NumberFormat.getIntegerInstance() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 2).coerceAtLeast(0))
    // Fully expanded: a partially expanded sheet would push the end of the list under the navigation bar.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(
            stringResource(R.string.rsvp_sections),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )
        LazyColumn(state = listState) {
            itemsIndexed(chapters) { position, chapter ->
                val isCurrent = position == currentIndex
                val skipped = chapter.kind != SectionKind.BODY
                val words = pluralStringResource(R.plurals.rsvp_word_count, chapter.wordCount, numberFormat.format(chapter.wordCount))
                val duration = formatDuration(Pacing.estimateMillis(chapter.wordCount, settings)) ?: stringResource(R.string.rsvp_less_than_a_minute)
                val dim = MaterialTheme.colorScheme.onSurfaceVariant
                ListItem(
                    headlineContent = {
                        Text(
                            chapter.displayTitle(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = if (skipped) dim else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    supportingContent = {
                        Text(
                            if (skipped) {
                                stringResource(R.string.rsvp_skipped)
                            } else {
                                stringResource(R.string.rsvp_section_details, words, duration)
                            },
                        )
                    },
                    trailingContent = {
                        if (isCurrent) {
                            Icon(painterResource(R.drawable.ic_check), contentDescription = null)
                        } else {
                            Text(stringResource(R.string.rsvp_section_pages, chapter.firstPage + 1), color = dim, style = MaterialTheme.typography.labelMedium)
                        }
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

/** The section's heading; for untitled ones, "Title page and contents" or its pages. */
@Composable
fun ChapterInfo.displayTitle(): String {
    title?.let { return it }
    if (kind == SectionKind.FRONT) return stringResource(R.string.rsvp_front_matter)
    return if (firstPage == lastPage) {
        stringResource(R.string.rsvp_page_single, firstPage + 1)
    } else {
        stringResource(R.string.rsvp_pages, firstPage + 1, lastPage + 1)
    }
}
