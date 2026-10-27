package io.github.fianna76.speedread.news.sports

import io.github.fianna76.speedread.news.FeedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SportsCalendarTest {

    private fun resource(name: String) = javaClass.getResource("/sports/$name")!!.readText()

    private fun millis(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun `ESPN team schedules become NBA games in UTC`() {
        val games = EspnNbaSchedule.parse(resource("espn-lakers-schedule.json"))
        assertEquals(3, games.size)
        val first = games.first()
        assertEquals("Warriors at Lakers", first.title)
        assertEquals(millis("2026-10-22T02:00:00Z"), first.startMillis)
        assertEquals(setOf("LAL", "GS"), first.follows)
        assertEquals(SportsEvent.Kind.NBA, first.kind)
        assertTrue(first.id.startsWith("nba:"))
        assertEquals("Clippers at Lakers", games[1].title)
    }

    @Test
    fun `ESPN scoreboards list broadcasters`() {
        val games = EspnNbaSchedule.parse(resource("espn-scoreboard.json"))
        assertEquals("Nets at Celtics", games[0].title)
        assertEquals("NBA League Pass, DAZN (YES)", games[0].detail)
        assertEquals("TD Garden", games[0].venue)
        assertEquals("NBC", games[1].detail)
    }

    @Test
    fun `sportstimes calendars become one event per session`() {
        val f2 = MotorsportCatalog.byId("f2")!!
        val sessions = SportsTimesCalendar.parse(resource("sportstimes-f2.json"), f2)
        assertEquals(8, sessions.size)
        val feature = sessions.first { it.detail == "Feature race" }
        assertEquals("F2 · Australian", feature.title)
        assertEquals(millis("2026-03-08T00:25:00Z"), feature.startMillis)
        assertEquals("Melbourne", feature.venue)
        assertEquals(setOf("f2"), feature.follows)
        assertEquals(sessions.size, sessions.map { it.id }.toSet().size)
        assertEquals("Grand Prix", SportsTimesCalendar.sessionName("gp"))
        assertEquals("Sprint qualifying", SportsTimesCalendar.sessionName("sprintQualifying"))
        assertEquals("Shakedown", SportsTimesCalendar.sessionName("shakedown"))
    }

    @Test
    fun `iCalendar files handle UTC, time zones, all-day dates, folded lines and escapes`() {
        val wec = MotorsportCatalog.byId("wec")!!
        val events = IcsCalendar.parse(resource("wec.ics"), wec)
        assertEquals(3, events.size) // The event without a start is skipped.
        val race = events[0]
        assertEquals("WEC · 8 Hours of Bahrain, Race", race.title)
        assertEquals(millis("2026-11-07T11:00:00Z"), race.startMillis)
        assertEquals(360, race.durationMinutes)
        assertEquals("Bahrain International Circuit, Sakhir", race.venue)
        assertEquals(millis("2026-11-05T12:00:00Z"), events[1].startMillis) // 15:00 in Bahrain (UTC+3).
        assertEquals(millis("2027-02-20T00:00:00Z"), events[2].startMillis)
        assertNull(events[2].venue)
    }

    @Test(expected = CalendarFormatException::class)
    fun `other documents are rejected`() {
        IcsCalendar.parse("<html></html>", MotorsportCatalog.byId("wec")!!)
    }

    @Test
    fun `NBA stories are matched to followed teams by name`() {
        val espn = SportsNews.sourceId(SportsNews.NBA_FEEDS[0].url)
        fun item(title: String) = FeedItem(espn, title, "https://espn.example/${title.hashCode()}", "", null, null, null)
        assertEquals(setOf("LAL"), SportsNews.follows(item("Lakers beat Warriors in overtime"), setOf("LAL", "BOS"), emptySet()))
        assertEquals(setOf("LAL", "BOS"), SportsNews.follows(item("Celtics trade for Lakers guard"), setOf("LAL", "BOS"), emptySet()))
        assertTrue(SportsNews.follows(item("Heat wave hits Miami"), setOf("LAL"), emptySet()).isEmpty())
        // Capitalised names only: "the heat" is weather, "the Heat" is Miami.
        assertTrue(SportsNews.follows(item("Players struggle in the heat"), setOf("MIA"), emptySet()).isEmpty())
        assertEquals(setOf("MIA"), SportsNews.follows(item("Butler returns as the Heat win"), setOf("MIA"), emptySet()))
    }

    @Test
    fun `series stories come from their feeds`() {
        val f1Feed = MotorsportCatalog.byId("f1")!!.news.first()
        val item = FeedItem(SportsNews.sourceId(f1Feed.url), "Verstappen on pole in Abu Dhabi", "https://m.example/1", "", null, null, null)
        assertEquals(setOf("f1"), SportsNews.follows(item, emptySet(), setOf("f1", "f2")))
        assertTrue(SportsNews.follows(item, emptySet(), setOf("f2")).isEmpty())
        val sources = SportsNews.sources(setOf("LAL"), setOf("f1"))
        assertEquals(SportsNews.NBA_FEEDS.size + MotorsportCatalog.byId("f1")!!.news.size, sources.size)
        assertTrue(SportsNews.sources(emptySet(), emptySet()).isEmpty())
    }

    @Test
    fun `Le Mans series race pages give each session with its time`() {
        val wec = MotorsportCatalog.byId("wec")!!
        val sessions = LeMansCalendar.parseRace(resource("wec-race-monza.html"), wec)
        assertEquals(8, sessions.size)
        val race = sessions.last()
        assertEquals("WEC \u00b7 6 Hours of Monza", race.title)
        assertEquals("Race", race.detail)
        assertEquals(millis("2026-11-08T10:00:00Z"), race.startMillis) // 11:00 in Monza (UTC+1).
        assertEquals(360, race.durationMinutes)
        assertEquals("Monza", race.venue)
        assertEquals("Qualifying - LMGT3", sessions[3].detail)
        assertEquals(sessions.size, sessions.map { it.id }.toSet().size)
    }

    @Test
    fun `races without session times yet become an all-weekend entry`() {
        val elms = MotorsportCatalog.byId("elms")!!
        val weekend = LeMansCalendar.parseRace(resource("elms-race-barcelona-2027.html"), elms).single()
        assertTrue(weekend.allDay)
        assertEquals("ELMS \u00b7 4 Hours of Barcelona", weekend.title)
        assertEquals(millis("2027-03-12T00:00:00Z"), weekend.startMillis)
        assertEquals(3 * 24 * 60, weekend.durationMinutes)
    }

    @Test
    fun `season pages list race pages and their years`() {
        val paths = LeMansCalendar.racePaths(resource("wec-season.html"))
        assertTrue(paths.contains("/en/race/6-hours-of-monza-2026"))
        assertTrue(paths.contains("/en/race/qatar-1812km-2027"))
        assertEquals(paths.size, paths.toSet().size)
        assertEquals(2027, LeMansCalendar.yearOf("/en/race/qatar-1812km-2027"))
        assertNull(LeMansCalendar.yearOf("/en/race/test-day"))
    }
}
