package io.github.fianna76.studypack.ui.rsvp

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.fianna76.speedread.rsvp.TokenizedText

/**
 * The section as ordinary text, for skimming, re-reading or finding a place. The current word
 * is highlighted; tapping a word moves the reading position there (the reader then returns to
 * speed reading).
 */
@Composable
fun PageView(
    text: TokenizedText,
    currentIndex: Int,
    onWordTapped: (Int) -> Unit,
    modifier: Modifier = Modifier,
    header: String? = null,
) {
    val paragraphs = remember(text) { text.paragraphRanges() }
    val currentParagraph = paragraphs.indexOfFirst { currentIndex in it }.coerceAtLeast(0)
    // The header, if any, is item 0.
    val offset = if (header != null) 1 else 0
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentParagraph + offset)
    LaunchedEffect(text) { listState.scrollToItem(currentParagraph + offset) }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        if (header != null) {
            item(key = "header") {
                Text(
                    header,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
        itemsIndexed(paragraphs, key = { _, range -> range.first }) { _, range ->
            PageParagraph(text, range, currentIndex, onWordTapped)
        }
    }
}

@Composable
private fun PageParagraph(text: TokenizedText, range: IntRange, currentIndex: Int, onWordTapped: (Int) -> Unit) {
    val highlight = SpanStyle(background = MaterialTheme.colorScheme.primaryContainer, color = MaterialTheme.colorScheme.onPrimaryContainer)
    // Character offset at which each word of the paragraph starts.
    val starts = remember(text, range) {
        var offset = 0
        range.map { index -> offset.also { offset += text[index].text.length + 1 } }
    }
    val annotated: AnnotatedString = remember(text, range, currentIndex, highlight) {
        buildAnnotatedString {
            range.forEachIndexed { i, index ->
                if (i > 0) append(' ')
                if (index == currentIndex) pushStyle(highlight)
                append(text[index].text)
                if (index == currentIndex) pop()
            }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.15f),
        onTextLayout = { layout = it },
        modifier = Modifier
            .widthIn(max = 640.dp)
            .pointerInput(range) {
                detectTapGestures { position ->
                    val offset = layout?.getOffsetForPosition(position) ?: return@detectTapGestures
                    val word = starts.indexOfLast { it <= offset }.coerceAtLeast(0)
                    onWordTapped(range.first + word)
                }
            },
    )
}
