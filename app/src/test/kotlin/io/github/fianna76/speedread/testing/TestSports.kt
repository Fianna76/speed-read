package io.github.fianna76.speedread.testing

import io.github.fianna76.speedread.news.FeedItem
import io.github.fianna76.speedread.news.sports.SportsEvent
import io.github.fianna76.speedread.ui.sports.SportsStory
import io.github.fianna76.speedread.ui.sports.SportsUiState
import java.time.Instant

/** A Sports section following the Lakers, the Celtics, F1 and the WEC and ELMS, plus news-only Supercars. */
object TestSports {

    /** Tuesday 27 October 2026, 09:00 UTC. */
    val NOW: Long = Instant.parse("2026-10-27T09:00:00Z").toEpochMilli()

    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun story(slug: String, title: String, outlet: String, about: String, minutesAgo: Long) = SportsStory(
        item = FeedItem("sport-$slug", title, "https://$slug.example/story", "What happened and what comes next.", null, NOW - minutesAgo * 60_000, null),
        outlet = outlet,
        about = listOf(about),
    )

    val events = listOf(
        SportsEvent("nba:1", "Celtics at Lakers", "ESPN", at("2026-10-28T02:30:00Z"), 150, "Crypto.com Arena", setOf("LAL", "BOS"), SportsEvent.Kind.NBA),
        SportsEvent("f1:mex:fp1", "F1 · Mexico City", "Practice 1", at("2026-10-30T18:30:00Z"), 60, "Mexico City", setOf("f1"), SportsEvent.Kind.MOTORSPORT),
        SportsEvent("f1:mex:q", "F1 · Mexico City", "Qualifying", at("2026-10-31T22:00:00Z"), 60, "Mexico City", setOf("f1"), SportsEvent.Kind.MOTORSPORT),
        SportsEvent("wec:bah", "WEC · 8 Hours of Bahrain", "Race", at("2026-11-07T11:00:00Z"), 480, "Sakhir", setOf("wec"), SportsEvent.Kind.MOTORSPORT),
        SportsEvent("elms:bcn", "ELMS · 4 Hours of Barcelona", null, at("2026-11-13T00:00:00Z"), 3 * 1440, null, setOf("elms"), SportsEvent.Kind.MOTORSPORT, allDay = true),
    )

    fun state() = SportsUiState(
        teams = setOf("LAL", "BOS"),
        series = setOf("f1", "wec", "elms", "supercars"),
        hasLoaded = true,
        events = events,
        stories = listOf(
            story("verstappen", "Verstappen sets the pace in Mexico as Ferrari struggle for grip", "Motorsport.com", "F1", 35),
            story("lakers", "Lakers hold off the Celtics behind a 40-point night", "ESPN", "Lakers", 80),
            story("toyota", "Toyota seals the WEC manufacturers' title with a race to spare", "Sportscar365", "WEC", 300),
        ),
    )
}
