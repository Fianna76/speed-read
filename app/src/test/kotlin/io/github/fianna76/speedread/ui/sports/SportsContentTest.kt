package io.github.fianna76.speedread.ui.sports

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.speedread.news.sports.SportsEvent
import io.github.fianna76.speedread.testing.PHONE
import io.github.fianna76.speedread.testing.TestSports
import io.github.fianna76.speedread.ui.theme.SpeedReadTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rGB-$PHONE")
class SportsContentTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(state: SportsUiState = TestSports.state(), zone: String = "Europe/Dublin", tab: Int = 1, actions: SportsActions = SportsActions()) {
        compose.setContent {
            SpeedReadTheme(dynamicColor = false) { SportsContent(state, TestSports.NOW, ZoneId.of(zone), actions, initialTab = tab) }
        }
    }

    @Test
    fun `the calendar shows start times in the user's time zone`() {
        show(zone = "Europe/Dublin")
        // 02:30 UTC on the 28th is 02:30 in Dublin (GMT after the clocks went back)...
        compose.onNodeWithText("Tomorrow, Wednesday 28 October").assertExists()
        compose.onNodeWithText("02:30").assertExists()
        compose.onNodeWithText("Times in your time zone (Europe/Dublin)").assertExists()
    }

    @Test
    fun `the same game is on the previous evening in Los Angeles`() {
        show(zone = "America/Los_Angeles")
        compose.onNodeWithText("Today, Tuesday 27 October").assertExists()
        compose.onNodeWithText("19:30").assertExists()
        compose.onNodeWithText("Times in your time zone (America/Los Angeles)").assertExists()
    }

    @Test
    fun `weekends without session times are all-day entries, and news-only series are explained`() {
        show()
        compose.onNodeWithText("News only, no public calendar: Supercars").assertExists()
        compose.onNodeWithText("ELMS · 4 Hours of Barcelona").performScrollTo()
        compose.onNodeWithText("All day").assertExists()
        compose.onNodeWithText("Friday 13 November").assertExists()
    }

    @Test
    fun `chips narrow the calendar, and everything shown can go into a calendar`() {
        var exported: List<SportsEvent> = emptyList()
        var single: SportsEvent? = null
        show(actions = SportsActions(onAddAll = { exported = it }, onAddEvent = { single = it }))

        compose.onNodeWithText("Add 5 events to calendar").performClick()
        assertEquals(TestSports.events, exported)

        compose.onNodeWithText("F1").performClick()
        compose.onNodeWithText("F1").assertIsSelected()
        compose.onNodeWithText("Add 2 events to calendar").performClick()
        assertEquals(2, exported.size)
        assertEquals(0, compose.onAllNodesWithText("Celtics at Lakers").fetchSemanticsNodes().size)

        compose.onAllNodesWithContentDescription("Add F1 · Mexico City to calendar")[1].performClick()
        assertEquals("Qualifying", single?.detail)
    }

    @Test
    fun `with nothing followed, the picker stays up until the user is done choosing`() {
        var state by mutableStateOf(SportsUiState(hasLoaded = true))
        val follows = mutableListOf<Pair<String, Boolean>>()
        fun follow(id: String, on: Boolean) {
            follows += id to on
            state = if (id.first().isUpperCase()) state.copy(teams = state.teams + id) else state.copy(series = state.series + id)
        }
        compose.setContent {
            SpeedReadTheme(dynamicColor = false) {
                SportsContent(state, TestSports.NOW, ZoneId.of("Europe/Dublin"), SportsActions(onTeamFollowed = ::follow, onSeriesFollowed = ::follow))
            }
        }
        compose.onNodeWithText("Done").assertIsNotEnabled()
        compose.onNodeWithText("Formula 1").performClick()
        compose.onNodeWithText("Boston Celtics").performScrollTo().performClick()
        assertEquals(listOf("f1" to true, "BOS" to true), follows)

        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Calendar").assertExists()
    }

    @Test
    fun `latest stories say what they are about`() {
        var read: String? = null
        show(tab = 0, actions = SportsActions(onReadInApp = { read = it.title }))
        compose.onNodeWithText("F1 · Motorsport.com", substring = true).assertExists()
        compose.onAllNodesWithText("Speed-read")[0].performClick()
        assertEquals("Verstappen sets the pace in Mexico as Ferrari struggle for grip", read)
    }
}
