package io.github.fianna76.speedread.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.fianna76.speedread.R
import io.github.fianna76.speedread.SpeedReadApplication
import io.github.fianna76.speedread.data.stats.ReadingDay
import io.github.fianna76.speedread.data.stats.ReadingStats
import io.github.fianna76.speedread.data.stats.ReadingSummary
import io.github.fianna76.speedread.ui.reader.formatDuration
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.text.NumberFormat
import java.time.format.TextStyle
import java.util.Locale

class StatsViewModel(stats: ReadingStats) : ViewModel() {
    /** `null` until loaded. */
    val summary: StateFlow<ReadingSummary?> = stats.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { StatsViewModel((this[APPLICATION_KEY] as SpeedReadApplication).container.readingStats) }
        }
    }
}

@Composable
fun StatsScreen(onBack: () -> Unit, viewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory)) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    StatsContent(summary, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsContent(summary: ReadingSummary?, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (summary == null) return@Column
            if (summary.totalWords == 0L) {
                Text(
                    text = stringResource(R.string.stats_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
                return@Column
            }
            StreakCard(summary)
            WeekChart(summary.week)
            val numbers = remember { NumberFormat.getIntegerInstance() }
            val lessThanAMinute = stringResource(R.string.reader_less_than_a_minute)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    label = stringResource(R.string.stats_today),
                    value = numbers.format(summary.today.words),
                    detail = stringResource(R.string.stats_words_label) + " · " + (formatDuration(summary.today.millis) ?: lessThanAMinute),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.stats_this_week),
                    value = numbers.format(summary.weekWords),
                    detail = stringResource(R.string.stats_words_label),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    label = stringResource(R.string.stats_average_speed),
                    value = numbers.format(summary.averageWpm),
                    detail = stringResource(R.string.stats_wpm_label),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.stats_time_saved),
                    value = formatDuration(summary.millisSaved.coerceAtLeast(0)) ?: lessThanAMinute,
                    detail = stringResource(R.string.stats_time_saved_detail, ReadingSummary.TYPICAL_WPM),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    label = stringResource(R.string.stats_total),
                    value = numbers.format(summary.totalWords),
                    detail = (formatDuration(summary.totalMillis) ?: lessThanAMinute),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.stats_longest_streak),
                    value = numbers.format(summary.longestStreak),
                    detail = pluralStringResource(R.plurals.stats_days_label, summary.longestStreak),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StreakCard(summary: ReadingSummary) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = pluralStringResource(R.plurals.stats_streak, summary.streak, summary.streak),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(if (summary.today.words > 0) R.string.stats_streak_today else R.string.stats_streak_keep),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, detail: String, modifier: Modifier = Modifier) {
    OutlinedCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/**
 * Words read on each of the last seven days: one series, so one hue and no legend. Bars have
 * rounded tops on a shared baseline. Only the selected day (today by default; tap another) is
 * labelled with its value. The whole chart is described for TalkBack.
 */
@Composable
private fun WeekChart(week: List<ReadingDay>) {
    var selected by rememberSaveable { mutableIntStateOf(week.lastIndex) }
    val numbers = remember { NumberFormat.getIntegerInstance() }
    val measurer = rememberTextMeasurer()
    val bar = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.surfaceVariant
    val baseline = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val valueStyle = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
    val locale = Locale.getDefault()
    val days = week.map { it.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) }
    val description = stringResource(R.string.stats_week_chart) + ": " +
        week.indices.joinToString(", ") { "${days[it]} ${numbers.format(week[it].words)}" }
    val max = (week.maxOfOrNull { it.words } ?: 0).coerceAtLeast(1)

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.stats_week_chart), style = MaterialTheme.typography.titleSmall)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(top = 8.dp)
                    .semantics { contentDescription = description }
                    .pointerInput(week) {
                        detectTapGestures { offset -> selected = (offset.x / (size.width / week.size)).toInt().coerceIn(0, week.lastIndex) }
                    },
            ) {
                val labelHeight = 20.dp.toPx()
                val valueHeight = 20.dp.toPx()
                val plotTop = valueHeight
                val plotBottom = size.height - labelHeight
                val slot = size.width / week.size
                val barWidth = (slot * 0.5f).coerceAtMost(28.dp.toPx())
                drawLine(baseline, Offset(0f, plotBottom), Offset(size.width, plotBottom), 1.dp.toPx())
                week.forEachIndexed { i, day ->
                    val x = slot * i + (slot - barWidth) / 2
                    val height = (plotBottom - plotTop) * day.words / max
                    if (day.words > 0) {
                        drawTopRoundedBar(x, plotBottom, barWidth, height.coerceAtLeast(2.dp.toPx()), if (i == selected) bar else bar.copy(alpha = 0.55f))
                    } else {
                        drawRoundRect(muted, Offset(x, plotBottom - 2.dp.toPx()), Size(barWidth, 2.dp.toPx()))
                    }
                    val label = measurer.measure(days[i], labelStyle)
                    drawText(label, topLeft = Offset(slot * i + (slot - label.size.width) / 2, plotBottom + 4.dp.toPx()))
                    if (i == selected) {
                        val value = measurer.measure(numbers.format(day.words), valueStyle)
                        val top = (plotBottom - height - value.size.height - 2.dp.toPx()).coerceAtLeast(0f)
                        drawText(value, topLeft = Offset((slot * i + (slot - value.size.width) / 2).coerceIn(0f, size.width - value.size.width), top))
                    }
                }
            }
        }
    }
}

/** A bar with 4dp rounded corners on top and a square foot on the baseline. */
private fun DrawScope.drawTopRoundedBar(x: Float, baseline: Float, width: Float, height: Float, color: androidx.compose.ui.graphics.Color) {
    val radius = minOf(4.dp.toPx(), width / 2, height)
    val path = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = x, top = baseline - height, right = x + width, bottom = baseline,
                topLeftCornerRadius = CornerRadius(radius), topRightCornerRadius = CornerRadius(radius),
                bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero,
            ),
        )
    }
    drawPath(path, color)
}
