package io.github.fianna76.speedread.data.stats

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.speedread.data.db.SpeedReadDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class ReadingStatsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, SpeedReadDatabase::class.java).allowMainThreadQueries().build()
    private val today = LocalDate.of(2026, 10, 18)

    @After
    fun tearDown() = database.close()

    private fun day(daysAgo: Long, words: Long, minutes: Long = words / 300) = ReadingDay(today.minusDays(daysAgo), words, minutes * 60_000)

    @Test
    fun `streaks count consecutive days ending today or yesterday`() {
        val days = listOf(day(9, 100), day(8, 100), day(7, 100), day(2, 100), day(1, 300))
        val summary = ReadingSummary.of(days, today)
        assertEquals(2, summary.streak) // Yesterday and the day before; today not read yet.
        assertEquals(3, summary.longestStreak)
        assertEquals(0, ReadingSummary.of(listOf(day(2, 100)), today).streak)
        assertEquals(3, ReadingSummary.of(days + day(0, 50), today).streak)
    }

    @Test
    fun `the week runs oldest first, with empty days, and totals add up`() {
        val summary = ReadingSummary.of(listOf(day(10, 3_000, 10), day(0, 600, 2)), today)
        assertEquals(7, summary.week.size)
        assertEquals(today, summary.week.last().date)
        assertEquals(listOf(0L, 0, 0, 0, 0, 0, 600), summary.week.map { it.words })
        assertEquals(600, summary.weekWords)
        assertEquals(3_600, summary.totalWords)
        assertEquals(300, summary.averageWpm) // 3,600 words in 12 minutes.
        assertEquals(null, ReadingSummary.of(listOf(ReadingDay(today, 30, 5_000)), today).averageWpm) // Too little to tell.
        // 3,600 words at 250 wpm is 14.4 minutes: 2.4 minutes saved.
        assertEquals(144_000, summary.millisSaved)
    }

    @Test
    fun `reading is recorded per day, and skipped stretches are capped`() = runTest {
        var now = today.atTime(9, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val stats = ReadingStats(database.readingDayDao(), clock = { now }, zone = { ZoneOffset.UTC })

        stats.record(words = 300, millis = 60_000)
        stats.record(words = 5_000, millis = 60_000) // Skipping, not reading: at most 1,000 in a minute.
        stats.record(words = 0, millis = 1_000)
        now += 24 * 60 * 60 * 1000L
        stats.record(words = 100, millis = 30_000)

        val summary = stats.observe().first()
        assertEquals(LocalDate.of(2026, 10, 19), summary.today.date)
        assertEquals(100, summary.today.words)
        assertEquals(1_400, summary.totalWords)
        assertEquals(2, summary.streak)
    }
}
