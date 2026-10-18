package io.github.fianna76.speedread.data.stats

import io.github.fianna76.speedread.data.db.ReadingDayDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong

/** Words and time read on one day. */
data class ReadingDay(val date: LocalDate, val words: Long, val millis: Long)

/** The statistics screen's figures. */
data class ReadingSummary(
    val today: ReadingDay,
    /** The last seven days, oldest first, today last (days without reading are zero). */
    val week: List<ReadingDay>,
    val totalWords: Long,
    val totalMillis: Long,
    /** Consecutive days with reading, ending today (or yesterday, if today has none yet). */
    val streak: Int,
    val longestStreak: Int,
) {
    /** Average reading speed, words per minute; 0 before any reading. */
    val averageWpm: Int get() = if (totalMillis < 60_000) 0 else (totalWords * 60_000.0 / totalMillis).roundToLong().toInt()

    /** Time saved compared with reading the same words at [TYPICAL_WPM]. */
    val millisSaved: Long get() = (totalWords * 60_000.0 / TYPICAL_WPM).roundToLong() - totalMillis

    val weekWords: Long get() = week.sumOf { it.words }

    companion object {
        /** A typical silent reading speed for adults. */
        const val TYPICAL_WPM = 250

        fun of(days: List<ReadingDay>, today: LocalDate): ReadingSummary {
            val byDate = days.associateBy { it.date }
            fun day(date: LocalDate) = byDate[date] ?: ReadingDay(date, 0, 0)
            val read = days.filter { it.words > 0 }.mapTo(HashSet()) { it.date }
            var streak = 0
            var cursor = if (today in read) today else today.minusDays(1)
            while (cursor in read) {
                streak++
                cursor = cursor.minusDays(1)
            }
            var longest = 0
            var run = 0
            var previous: LocalDate? = null
            for (date in read.sorted()) {
                run = if (previous != null && previous.plusDays(1) == date) run + 1 else 1
                longest = maxOf(longest, run)
                previous = date
            }
            return ReadingSummary(
                today = day(today),
                week = (6 downTo 0).map { day(today.minusDays(it.toLong())) },
                totalWords = days.sumOf { it.words },
                totalMillis = days.sumOf { it.millis },
                streak = streak,
                longestStreak = longest,
            )
        }
    }
}

/** Records reading time and words, per local day. */
class ReadingStats(
    private val dao: ReadingDayDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {

    fun observe(): Flow<ReadingSummary> = dao.observeAll().map { rows ->
        ReadingSummary.of(rows.map { ReadingDay(LocalDate.parse(it.date), it.words, it.millis) }, today())
    }

    /** Adds [words] read in [millis] to today. Implausible stretches (a skip mid-play) are capped. */
    suspend fun record(words: Int, millis: Long) {
        if (words <= 0 || millis <= 0) return
        // Faster than the fastest speed means words were skipped, not read: count what could have been read.
        val readable = minOf(words.toLong(), (millis * MAX_WPM / 60_000.0).roundToLong())
        if (readable <= 0) return
        dao.add(today().toString(), readable, millis)
    }

    private fun today(): LocalDate = Instant.ofEpochMilli(clock()).atZone(zone()).toLocalDate()

    private companion object {
        const val MAX_WPM = 1_000
    }
}
