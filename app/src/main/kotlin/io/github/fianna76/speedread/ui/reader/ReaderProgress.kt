package io.github.fianna76.speedread.ui.reader

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import io.github.fianna76.speedread.rsvp.Pacing
import io.github.fianna76.speedread.rsvp.PlayerState
import java.util.Locale
import kotlin.math.ceil

/** Progress figures shown under the reading surface, derived from UI and player state. */
data class ReaderProgress(
    /** Progress through the current chapter, `[0, 1]`. */
    val chapterFraction: Float,
    /** Progress through the whole book, `[0, 1]`. */
    val bookFraction: Float,
    /** Estimated time to finish the book at the current speed and pacing. */
    val bookMillisLeft: Long,
) {
    companion object {
        fun of(ui: ReaderUiState, player: PlayerState): ReaderProgress {
            val chapter = ui.chapter
            val wordsRead = when {
                ui.isFinished -> ui.totalWords
                chapter == null -> 0
                else -> chapter.startWord + player.index
            }
            val total = ui.totalWords.coerceAtLeast(1)
            return ReaderProgress(
                chapterFraction = if (ui.isFinished) 1f else player.progress,
                bookFraction = (wordsRead.toFloat() / total).coerceIn(0f, 1f),
                bookMillisLeft = Pacing.estimateMillis((ui.totalWords - wordsRead).coerceAtLeast(0), player.settings),
            )
        }
    }
}

/**
 * Formats a duration as localised hours and minutes ("1 hr, 5 min"), rounding up to whole
 * minutes. Returns `null` for durations under a minute so callers can phrase those themselves.
 */
fun formatDuration(millis: Long, locale: Locale = Locale.getDefault()): String? {
    val totalMinutes = ceil(millis / 60_000.0).toLong()
    if (totalMinutes < 1) return null
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val format = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT)
    return when {
        hours == 0L -> format.format(Measure(minutes, MeasureUnit.MINUTE))
        minutes == 0L -> format.format(Measure(hours, MeasureUnit.HOUR))
        else -> format.formatMeasures(Measure(hours, MeasureUnit.HOUR), Measure(minutes, MeasureUnit.MINUTE))
    }
}
