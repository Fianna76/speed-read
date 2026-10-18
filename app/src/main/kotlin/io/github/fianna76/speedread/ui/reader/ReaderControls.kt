package io.github.fianna76.speedread.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.fianna76.speedread.R
import io.github.fianna76.speedread.rsvp.WordsPerMinute
import kotlin.math.roundToInt

/** Number of discrete stops on the speed slider (between the two ends). */
private const val WPM_SLIDER_STEPS = (WordsPerMinute.MAX - WordsPerMinute.MIN) / WordsPerMinute.STEP - 1

/**
 * Bottom panel of the reader: progress, the words-per-minute slider and transport buttons.
 * Everything here duplicates the tap zones in an explicit, accessible form.
 */
@Composable
fun ReaderControls(
    chapterLabel: String,
    progressLabel: String,
    chapterProgress: Float,
    wordsPerMinute: Int,
    isPlaying: Boolean,
    enabled: Boolean,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
    onWordsPerMinuteChange: (Int) -> Unit,
    onWordsPerMinuteChangeFinished: () -> Unit,
    onChangeWordsPerMinuteBy: (Int) -> Unit,
    onPlayPause: () -> Unit,
    onPreviousSentence: () -> Unit,
    onNextSentence: () -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    modifier: Modifier = Modifier,
    onScrub: (Float) -> Unit = {},
    onScrubFinished: () -> Unit = {},
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = chapterLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = progressLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            ChapterScrubber(chapterProgress, enabled, onScrub, onScrubFinished)

            SpeedRow(
                wordsPerMinute = wordsPerMinute,
                onChange = onWordsPerMinuteChange,
                onChangeFinished = onWordsPerMinuteChangeFinished,
                onChangeBy = onChangeWordsPerMinuteBy,
            )

            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPreviousChapter, enabled = enabled && hasPreviousChapter) {
                    Icon(painterResource(R.drawable.ic_skip_previous), stringResource(R.string.reader_previous_chapter))
                }
                FilledTonalIconButton(onClick = onPreviousSentence, enabled = enabled, modifier = Modifier.size(56.dp)) {
                    Icon(painterResource(R.drawable.ic_fast_rewind), stringResource(R.string.reader_previous_sentence))
                }
                FilledIconButton(onClick = onPlayPause, enabled = enabled, modifier = Modifier.size(72.dp)) {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                        contentDescription = stringResource(if (isPlaying) R.string.reader_pause else R.string.reader_play),
                        modifier = Modifier.size(36.dp),
                    )
                }
                FilledTonalIconButton(onClick = onNextSentence, enabled = enabled, modifier = Modifier.size(56.dp)) {
                    Icon(painterResource(R.drawable.ic_fast_forward), stringResource(R.string.reader_next_sentence))
                }
                IconButton(onClick = onNextChapter, enabled = enabled && hasNextChapter) {
                    Icon(painterResource(R.drawable.ic_skip_next), stringResource(R.string.reader_next_chapter))
                }
            }
        }
    }
}

/**
 * Position in the chapter. Dragging pauses and moves through the text, so the word on screen
 * (and the context under it) previews where reading will continue.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterScrubber(progress: Float, enabled: Boolean, onScrub: (Float) -> Unit, onScrubFinished: () -> Unit) {
    val description = stringResource(R.string.reader_position)
    val percent = stringResource(R.string.reader_position_percent, (progress * 100).roundToInt())
    val interactionSource = remember { MutableInteractionSource() }
    Slider(
        value = progress.coerceIn(0f, 1f),
        onValueChange = onScrub,
        onValueChangeFinished = onScrubFinished,
        enabled = enabled,
        interactionSource = interactionSource,
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = interactionSource,
                enabled = enabled,
                thumbSize = DpSize(4.dp, 22.dp),
            )
        },
        track = { state ->
            SliderDefaults.Track(
                sliderState = state,
                enabled = enabled,
                drawStopIndicator = null,
                thumbTrackGapSize = 3.dp,
                modifier = Modifier.height(6.dp),
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .semantics {
                contentDescription = description
                stateDescription = percent
            },
    )
}

@Composable
private fun SpeedRow(
    wordsPerMinute: Int,
    onChange: (Int) -> Unit,
    onChangeFinished: () -> Unit,
    onChangeBy: (Int) -> Unit,
) {
    val label = stringResource(R.string.reader_wpm, wordsPerMinute)
    val speedDescription = stringResource(R.string.reader_speed)
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChangeBy(-WordsPerMinute.STEP) }, enabled = wordsPerMinute > WordsPerMinute.MIN) {
            Icon(painterResource(R.drawable.ic_remove), stringResource(R.string.reader_slower))
        }
        Slider(
            value = wordsPerMinute.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            onValueChangeFinished = onChangeFinished,
            valueRange = WordsPerMinute.MIN.toFloat()..WordsPerMinute.MAX.toFloat(),
            steps = WPM_SLIDER_STEPS,
            colors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = speedDescription
                    stateDescription = label
                },
        )
        IconButton(onClick = { onChangeBy(WordsPerMinute.STEP) }, enabled = wordsPerMinute < WordsPerMinute.MAX) {
            Icon(painterResource(R.drawable.ic_add), stringResource(R.string.reader_faster))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 72.dp),
        )
    }
}
