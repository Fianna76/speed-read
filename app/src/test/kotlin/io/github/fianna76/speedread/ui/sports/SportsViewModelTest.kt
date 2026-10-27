package io.github.fianna76.speedread.ui.sports

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.speedread.data.BookFileStore
import io.github.fianna76.speedread.data.db.SpeedReadDatabase
import io.github.fianna76.speedread.data.news.ArticleRepository
import io.github.fianna76.speedread.data.news.NewsCache
import io.github.fianna76.speedread.data.news.NewsHistoryRepository
import io.github.fianna76.speedread.data.news.NewsRepository
import io.github.fianna76.speedread.data.sports.CalendarExportResult
import io.github.fianna76.speedread.data.sports.DeviceCalendar
import io.github.fianna76.speedread.data.sports.SportsCalendarRepository
import io.github.fianna76.speedread.news.sports.SportsEvent
import io.github.fianna76.speedread.news.sports.SportsNews
import io.github.fianna76.speedread.testing.FakeWebClient
import io.github.fianna76.speedread.testing.MainDispatcherRule
import io.github.fianna76.speedread.testing.testPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.IOException
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class SportsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: SpeedReadDatabase
    private val now = Instant.parse("2026-10-20T12:00:00Z").toEpochMilli()
    private val espnFeed = SportsNews.NBA_FEEDS.first().url
    private val web = FakeWebClient(
        mapOf(
            "${SportsCalendarRepository.NBA_TEAMS}/13/schedule" to javaClass.getResource("/sports/espn-lakers-schedule.json")!!.readText(),
            espnFeed to rss("Lakers sign veteran guard", "Knicks beat Celtics", "Heat and Lakers talk trade"),
        ),
    )
    private val written = mutableListOf<Pair<DeviceCalendar, List<SportsEvent>>>()
    private var writerFails = false

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, SpeedReadDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    private fun rss(vararg titles: String) = "<rss><channel><title>ESPN</title>" +
        titles.joinToString("") { "<item><title>$it</title><link>https://espn.example/${it.hashCode()}</link></item>" } +
        "</channel></rss>"

    private fun TestScope.createViewModel(): SportsViewModel {
        val viewModel = SportsViewModel(
            preferences = testPreferences(tmp.newFolder(), backgroundScope),
            news = NewsRepository(web, NewsCache(tmp.root.resolve("sports.json"))),
            calendar = SportsCalendarRepository(web, tmp.newFolder(), clock = { now }),
            articles = ArticleRepository(database.bookDao(), BookFileStore(tmp.newFolder()), web),
            history = NewsHistoryRepository(database.newsHistoryDao()),
            externalScope = backgroundScope,
            writer = { calendar, events ->
                if (writerFails) throw IOException("Read-only")
                written += calendar to events
                CalendarExportResult(added = events.size, updated = 0, unchanged = 0)
            },
            clock = { now },
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        return viewModel
    }

    @Test
    fun `nothing is fetched until a team or series is followed`() = runTest {
        val viewModel = createViewModel()
        val state = viewModel.uiState.first { it.hasLoaded }
        assertTrue(state.followsNothing)
        assertTrue(web.requests.isEmpty())
    }

    @Test
    fun `following a team brings its stories and upcoming games`() = runTest {
        val viewModel = createViewModel()
        viewModel.setTeamFollowed("LAL", true)

        val state = viewModel.uiState.first { it.events.isNotEmpty() && it.stories.isNotEmpty() }

        assertEquals(setOf("LAL"), state.teams)
        assertEquals(listOf("Warriors at Lakers", "Clippers at Lakers", "Lakers at Jazz"), state.events.map { it.title })
        // Only stories about the Lakers, labelled with the team.
        assertEquals(listOf("Lakers sign veteran guard", "Heat and Lakers talk trade"), state.stories.map { it.item.title })
        assertTrue(state.stories.all { it.about == listOf("Lakers") && it.outlet == "ESPN" })
    }

    @Test
    fun `events go into the chosen calendar, and failures are reported`() = runTest {
        val viewModel = createViewModel()
        viewModel.setTeamFollowed("LAL", true)
        val events = viewModel.uiState.first { it.events.size == 3 }.events
        val calendar = DeviceCalendar(id = 4, name = "Basketball", account = "me@gmail.com", isGoogle = true, isPrimary = false)

        viewModel.addToCalendar(calendar)
        val exported = viewModel.uiState.first { it.message != null }.message as SportsMessage.Exported
        assertEquals("Basketball", exported.calendar)
        assertEquals(3, exported.result.added)
        assertEquals(listOf(calendar to events), written)
        viewModel.onMessageShown(exported.id)

        writerFails = true
        viewModel.addToCalendar(calendar, events.take(1))
        assertTrue(viewModel.uiState.first { it.message != null }.message is SportsMessage.ExportFailed)
    }
}
