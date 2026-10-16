package io.github.fianna76.speedread.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.fianna76.speedread.R
import io.github.fianna76.speedread.data.Book
import io.github.fianna76.speedread.ui.theme.SpeedReadTheme
import java.io.File
import kotlin.math.roundToInt

@Composable
fun BookCard(
    book: Book,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val finished = book.progress >= 1f
    val progressLabel = when {
        book.wordsRead <= 0 -> stringResource(R.string.book_new)
        finished -> stringResource(R.string.book_finished)
        else -> stringResource(R.string.book_progress, (book.progress * 100).roundToInt().coerceIn(1, 99))
    }
    val selectedLabel = stringResource(R.string.book_selected)

    Card(
        modifier = modifier
            .clip(CardDefaults.shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .semantics(mergeDescendants = true) {
                if (selectionMode) {
                    this.selected = selected
                    if (selected) stateDescription = selectedLabel
                }
            },
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Box {
            BookCover(book, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
            if (book.wordsRead > 0 && !finished) {
                LinearProgressIndicator(
                    progress = { book.progress },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp),
                    trackColor = Color.Black.copy(alpha = 0.35f),
                    strokeCap = StrokeCap.Butt,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
            if (selectionMode) {
                Icon(
                    painter = painterResource(if (selected) R.drawable.ic_check_circle else R.drawable.ic_radio_button_unchecked),
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(26.dp)
                        .background(Color.Black.copy(alpha = 0.25f), CircleShape),
                )
            }
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            // Always two lines, so cards in the same row line up.
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = book.author ?: stringResource(R.string.book_unknown_author),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = progressLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (finished) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun BookCover(book: Book, modifier: Modifier = Modifier) {
    if (book.coverPath != null) {
        val file = remember(book.coverPath) { File(book.coverPath) }
        AsyncImage(
            model = file,
            contentDescription = null, // The title is announced by the card itself.
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        GeneratedCover(book.title, modifier)
    }
}

/** A typographic stand-in for books without a cover image, coloured deterministically by title. */
@Composable
private fun GeneratedCover(title: String, modifier: Modifier = Modifier) {
    val background = PLACEHOLDER_COLORS[Math.floorMod(title.hashCode(), PLACEHOLDER_COLORS.size)]
    Box(
        modifier = modifier.background(background).padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
                .size(width = 24.dp, height = 2.dp)
                .background(Color.White.copy(alpha = 0.6f), CircleShape),
        )
    }
}

private val PLACEHOLDER_COLORS = listOf(
    Color(0xFF3F51B5),
    Color(0xFF00796B),
    Color(0xFF6D4C41),
    Color(0xFF7B1FA2),
    Color(0xFFC62828),
    Color(0xFF2E7D32),
    Color(0xFF455A64),
    Color(0xFFAD1457),
)

@Preview(widthDp = 140)
@Composable
private fun BookCardPreview() {
    SpeedReadTheme(dynamicColor = false) {
        BookCard(
            book = previewBook(1, "Pride and Prejudice", "Jane Austen", wordsRead = 52_000),
            selectionMode = true,
            selected = true,
            onClick = {},
            onLongClick = {},
        )
    }
}
