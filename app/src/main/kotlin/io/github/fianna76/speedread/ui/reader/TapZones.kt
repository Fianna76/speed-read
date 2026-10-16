package io.github.fianna76.speedread.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.fianna76.speedread.R

/**
 * Invisible single-tap zones over the reading surface: left = previous sentence,
 * centre = play/pause, right = next sentence. A brief icon confirms each tap.
 */
@Composable
fun TapZones(
    isPlaying: Boolean,
    enabled: Boolean,
    onPreviousSentence: () -> Unit,
    onPlayPause: () -> Unit,
    onNextSentence: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxSize()) {
        TapZone(
            weight = 0.3f,
            iconRes = R.drawable.ic_fast_rewind,
            label = stringResource(R.string.reader_previous_sentence),
            enabled = enabled,
            onTap = onPreviousSentence,
        )
        TapZone(
            weight = 0.4f,
            // The icon shows the action that was just taken.
            iconRes = if (isPlaying) R.drawable.ic_play_arrow else R.drawable.ic_pause,
            label = stringResource(if (isPlaying) R.string.reader_pause else R.string.reader_play),
            enabled = enabled,
            onTap = onPlayPause,
        )
        TapZone(
            weight = 0.3f,
            iconRes = R.drawable.ic_fast_forward,
            label = stringResource(R.string.reader_next_sentence),
            enabled = enabled,
            onTap = onNextSentence,
        )
    }
}

@Composable
private fun RowScope.TapZone(
    weight: Float,
    iconRes: Int,
    label: String,
    enabled: Boolean,
    onTap: () -> Unit,
) {
    var taps by remember { mutableIntStateOf(0) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(taps) {
        if (taps > 0) {
            alpha.snapTo(0.9f)
            alpha.animateTo(0f, tween(durationMillis = 450))
        }
    }
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClickLabel = label,
                role = Role.Button,
            ) {
                taps++
                onTap()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .graphicsLayer { this.alpha = alpha.value }
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                .padding(12.dp)
                .size(32.dp),
        )
    }
}
