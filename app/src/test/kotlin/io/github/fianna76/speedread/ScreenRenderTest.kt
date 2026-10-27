package io.github.fianna76.speedread

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.speedread.data.Chapter
import io.github.fianna76.speedread.data.settings.UserPreferences
import io.github.fianna76.speedread.news.NewsSources
import io.github.fianna76.speedread.rsvp.PlayerState
import io.github.fianna76.speedread.rsvp.Tokenizer
import io.github.fianna76.speedread.testing.PHONE
import io.github.fianna76.speedread.testing.TestNews
import io.github.fianna76.speedread.testing.TestSports
import io.github.fianna76.speedread.testing.testBook
import io.github.fianna76.speedread.ui.library.LibraryContent
import io.github.fianna76.speedread.ui.library.LibraryUiState
import io.github.fianna76.speedread.ui.navigation.TopLevelDestination
import io.github.fianna76.speedread.ui.navigation.TopLevelNavigationBar
import io.github.fianna76.speedread.ui.news.BriefingRequest
import io.github.fianna76.speedread.ui.news.NewsstandActions
import io.github.fianna76.speedread.ui.news.NewsstandContent
import io.github.fianna76.speedread.ui.news.NewsStory
import io.github.fianna76.speedread.ui.news.NewsstandUiState
import io.github.fianna76.speedread.ui.reader.ReaderActions
import io.github.fianna76.speedread.ui.reader.ReaderContent
import io.github.fianna76.speedread.ui.reader.ReaderUiState
import io.github.fianna76.speedread.ui.reader.UpNextStory
import io.github.fianna76.speedread.ui.settings.SettingsContent
import io.github.fianna76.speedread.ui.sports.SportsActions
import io.github.fianna76.speedread.ui.sports.SportsContent
import io.github.fianna76.speedread.ui.sports.SportsUiState
import io.github.fianna76.speedread.ui.theme.SpeedReadTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders every screen in light and dark themes on a phone-sized display with real graphics.
 * Acts as a smoke test (each screen composes, lays out and draws without crashing) and writes
 * PNGs to `app/build/outputs/screenshots/` for visual review.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE)
class ScreenRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private val books = listOf(
        testBook(1, title = "Pride and Prejudice", author = "Jane Austen", totalWords = 120_000, wordsRead = 52_000),
        testBook(2, title = "Moby-Dick; or, The Whale", author = "Herman Melville"),
        testBook(3, title = "The Time Machine", author = "H. G. Wells", totalWords = 32_000, wordsRead = 32_000),
        testBook(4, title = "Meditations", author = "Marcus Aurelius", totalWords = 60_000, wordsRead = 9_000),
    )

    private val paragraph = Tokenizer.tokenize(
        listOf(
            "It is a truth universally acknowledged, that a single man in possession of a good fortune, " +
                "must be in want of a wife.",
        ),
    )
    private val readerUi = ReaderUiState(
        isLoading = false,
        title = "Pride and Prejudice",
        chapters = List(61) { Chapter(it, "Chapter ${it + 1}", "c$it.xhtml", 2_000, it * 2_000) },
        chapterIndex = 26,
        totalWords = 122_000,
    )

    @Test
    fun library() = capture("library") {
        LibraryContent(
            state = LibraryUiState(isLoading = false, books = books),
            onImport = {}, onOpenBook = {}, onOpenSettings = {}, onSortChange = {}, onToggleSelection = {},
            onSelectAll = {}, onClearSelection = {}, onDeleteSelected = {}, onMessageShown = {},
            bottomBar = { TopLevelNavigationBar(TopLevelDestination.LIBRARY) {} },
        )
    }

    @Test
    fun librarySelection() = capture("library_selection") {
        LibraryContent(
            state = LibraryUiState(isLoading = false, books = books, selection = setOf(2L, 4L)),
            onImport = {}, onOpenBook = {}, onOpenSettings = {}, onSortChange = {}, onToggleSelection = {},
            onSelectAll = {}, onClearSelection = {}, onDeleteSelected = {}, onMessageShown = {},
        )
    }

    @Test
    fun libraryEmpty() = capture("library_empty") {
        LibraryContent(
            state = LibraryUiState(isLoading = false),
            onImport = {}, onOpenBook = {}, onOpenSettings = {}, onSortChange = {}, onToggleSelection = {},
            onSelectAll = {}, onClearSelection = {}, onDeleteSelected = {}, onMessageShown = {},
        )
    }

    private val now = 1_792_252_800_000L
    private val newsUi = NewsstandUiState(
        hasLoaded = true,
        sources = NewsSources.ALL,
        stories = TestNews.sampleArticles(now).let { a ->
            listOf(NewsStory(a[0].copy(isSaved = true), listOf(a[2].copy(source = NewsSources.byId("npr")!!))), NewsStory(a[1]), NewsStory(a[2].copy(isRead = true)))
        },
        continueReading = TestNews.sampleArticles(now).take(2).map { it.copy(progress = 0.3f) },
        savedCount = 1,
        lastUpdated = now - 30_000,
    )

    @Test
    fun newsstand() = capture("newsstand") {
        NewsstandContent(
            state = newsUi,
            now = now,
            actions = NewsstandActions(),
            bottomBar = { TopLevelNavigationBar(TopLevelDestination.NEWS) {} },
        )
    }

    @Test
    fun newsstandBriefing() = capture("newsstand_briefing") {
        NewsstandContent(
            state = newsUi.copy(
                briefing = TestNews.sampleArticles(now).let { a -> listOf(NewsStory(a[2]), NewsStory(a[0], listOf(a[1])), NewsStory(a[1])) },
                continueReading = emptyList(),
            ),
            now = now,
            actions = NewsstandActions(),
            briefingRequest = BriefingRequest.SHOW,
        )
    }

    @Test
    fun newsstandFilteredAndPreparing() = capture("newsstand_filtered") {
        NewsstandContent(
            state = newsUi.copy(
                selectedSourceId = "bbc",
                stories = newsUi.stories.take(1),
                failedSources = listOf(NewsSources.byId("bbc")!!),
                preparingUrl = newsUi.articles.first().item.canonicalUrl,
            ),
            now = now,
            actions = NewsstandActions(),
        )
    }

    @Test
    fun newsstandOffline() = capture("newsstand_offline") {
        NewsstandContent(
            state = NewsstandUiState(hasLoaded = true, sources = NewsSources.ALL, failedSources = NewsSources.ALL),
            now = now,
            actions = NewsstandActions(),
        )
    }

    @Test
    fun readerArticle() = capture("reader_article") {
        ReaderContent(
            readerUi.copy(
                title = "Pope discharged from hospital days after lung cancer surgery",
                sourceUrl = "https://www.bbc.co.uk/news/articles/c5x2vkddrkrko",
                sourceName = "BBC News",
                chapters = listOf(Chapter(0, null, "article", 412, 0)),
                chapterIndex = 0,
                totalWords = 412,
            ),
            PlayerState(text = paragraph, index = 13),
            ReaderActions(),
        )
    }

    @Test
    fun readerUpNext() = capture("reader_up_next") {
        ReaderContent(
            readerUi.copy(
                title = "Talks on public sector pay deal to begin at WRC on Monday",
                sourceUrl = "https://www.rte.ie/news/ireland/2026/1017/1595725-pay-talks-meeting/",
                sourceName = "RT\u00C9 News",
                chapters = listOf(Chapter(0, null, "article", 828, 0)),
                chapterIndex = 0,
                totalWords = 828,
                isFinished = true,
                upNext = UpNextStory(
                    TestNews.item("rte_brainstorm", "mick", "How 'the runt of the litter' became champion dog Mick the Miller"),
                    "RT\u00C9 Brainstorm",
                ),
                autoContinueIn = 3,
            ),
            PlayerState(text = paragraph, index = 22),
            ReaderActions(),
        )
    }

    @Test
    fun readerPlaying() = capture("reader_playing") {
        ReaderContent(readerUi, PlayerState(text = paragraph, index = 3, isPlaying = true), ReaderActions())
    }

    @Test
    fun readerPaused() = capture("reader_paused") {
        ReaderContent(readerUi, PlayerState(text = paragraph, index = 13), ReaderActions())
    }

    @Test
    fun readerFinished() = capture("reader_finished") {
        ReaderContent(readerUi.copy(isFinished = true), PlayerState(text = paragraph, index = 22), ReaderActions())
    }

    @Test
    fun stats() = capture("stats") {
        val today = java.time.LocalDate.of(2026, 10, 18)
        val days = listOf(6 to 1_800L, 5 to 0L, 4 to 2_400L, 3 to 3_100L, 2 to 900L, 1 to 4_200L, 0 to 2_700L, 12 to 9_000L)
            .map { (ago, words) -> io.github.fianna76.speedread.data.stats.ReadingDay(today.minusDays(ago.toLong()), words, words * 60_000 / 340) }
        io.github.fianna76.speedread.ui.stats.StatsContent(io.github.fianna76.speedread.data.stats.ReadingSummary.of(days, today), onBack = {})
    }

    @Test
    fun sportsNews() = capture("sports_news") {
        SportsContent(TestSports.state(), TestSports.NOW, java.time.ZoneId.of("Europe/Dublin"), SportsActions())
    }

    @Test
    fun sportsCalendar() = capture("sports_calendar") {
        SportsContent(TestSports.state(), TestSports.NOW, java.time.ZoneId.of("Europe/Dublin"), SportsActions(), initialTab = 1)
    }

    @Test
    fun sportsFollow() = capture("sports_follow") {
        SportsContent(SportsUiState(hasLoaded = true), TestSports.NOW, java.time.ZoneId.of("Europe/Dublin"), SportsActions())
    }

    @Test
    fun settings() = capture("settings") {
        SettingsContent(
            preferences = UserPreferences(),
            onBack = {}, onWordsPerMinuteChange = {}, onSmartPacingChange = {}, onRampUpChange = {},
            onFontSizeChange = {}, onFontChange = {}, onHighlightChange = {}, onShowContextChange = {},
            onThemeModeChange = {},
        )
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        var dark by mutableStateOf(false)
        compose.setContent { SpeedReadTheme(darkTheme = dark, dynamicColor = false, content = content) }
        save(name)
        dark = true
        compose.waitForIdle()
        save("${name}_dark")
    }

    private fun save(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = File("build/outputs/screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
