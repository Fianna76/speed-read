package io.github.fianna76.speedread.data.sports

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.speedread.news.sports.CalendarSource
import io.github.fianna76.speedread.news.sports.MotorsportCatalog
import io.github.fianna76.speedread.testing.FakeWebClient
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class SportsCalendarRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun resource(name: String) = javaClass.getResource("/sports/$name")!!.readText()

    private val lakers = "${SportsCalendarRepository.NBA_TEAMS}/13/schedule"
    private val warriors = "${SportsCalendarRepository.NBA_TEAMS}/9/schedule"
    private val f2 = (MotorsportCatalog.byId("f2")!!.calendar as CalendarSource.SportsTimes).url(2026)
    private var now = Instant.parse("2026-03-01T00:00:00Z").toEpochMilli()

    @Test
    fun `followed teams and series are fetched, merged and sorted, without duplicate games`() = runTest {
        val schedule = resource("espn-lakers-schedule.json")
        val web = FakeWebClient(mapOf(lakers to schedule, warriors to schedule, f2 to resource("sportstimes-f2.json")))
        val repository = SportsCalendarRepository(web, tmp.root, clock = { now }, ioDispatcher = StandardTestDispatcher(testScheduler))

        repository.refresh(setOf("LAL", "GS"), setOf("f2", "supercars"))

        val events = repository.state.value.upcoming(setOf("LAL", "GS"), setOf("f2"), now)
        assertEquals(8 + 3, events.size) // The Warriors' copy of each game is dropped.
        assertEquals(events.sortedBy { it.startMillis }, events)
        assertEquals("F2 · Australian", events.first().title)
        assertEquals(1, events.count { it.title == "Warriors at Lakers" })
        // Supercars has no public calendar, so nothing is fetched for it.
        assertEquals(setOf(lakers, warriors, f2), web.requests.toSet())
        // Only the followed: unfollowing the Lakers keeps the Warriors' games.
        assertEquals(3, repository.state.value.upcoming(setOf("GS"), emptySet(), now).size)
    }

    @Test
    fun `calendars are refreshed twice a day, kept on failure and cached on disk`() = runTest {
        val web = FakeWebClient(mapOf(lakers to resource("espn-lakers-schedule.json")))
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = SportsCalendarRepository(web, tmp.root, clock = { now }, ioDispatcher = dispatcher)
        repository.refresh(setOf("LAL"), emptySet())
        repository.refresh(setOf("LAL"), emptySet())
        assertEquals(1, web.requests.size) // Still fresh.

        now += SportsCalendarRepository.STALE_AFTER_MILLIS
        web.responses.clear()
        repository.refresh(setOf("LAL"), emptySet())
        assertEquals(setOf("nba:LAL"), repository.state.value.failed)
        assertEquals(3, repository.state.value.upcoming(setOf("LAL"), emptySet(), now).size) // Old schedule kept.

        // A new process starts from the cache, without fetching.
        val restarted = SportsCalendarRepository(web, tmp.root, clock = { now - 1 }, ioDispatcher = dispatcher)
        restarted.refresh(setOf("LAL"), emptySet())
        assertEquals(3, restarted.state.value.upcoming(setOf("LAL"), emptySet(), now).size)
        assertEquals(2, web.requests.size)
        assertTrue(restarted.state.value.failed.isEmpty())
    }

    @Test
    fun `finished events are not upcoming`() = runTest {
        val web = FakeWebClient(mapOf(lakers to resource("espn-lakers-schedule.json")))
        val repository = SportsCalendarRepository(web, tmp.root, clock = { now }, ioDispatcher = StandardTestDispatcher(testScheduler))
        repository.refresh(setOf("LAL"), emptySet())

        val laterOn = Instant.parse("2026-10-24T03:00:00Z").toEpochMilli() // The second game is under way.
        assertEquals(listOf("Clippers at Lakers", "Lakers at Jazz"), repository.state.value.upcoming(setOf("LAL"), emptySet(), laterOn).map { it.title })
    }
}
