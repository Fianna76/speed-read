package io.github.fianna76.speedread

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.speedread.data.Bookmark
import io.github.fianna76.speedread.data.BookCollection
import io.github.fianna76.speedread.data.Chapter
import io.github.fianna76.speedread.data.ReadingPosition
import io.github.fianna76.speedread.data.settings.UserPreferences
import io.github.fianna76.speedread.data.stats.ReadingDay
import io.github.fianna76.speedread.data.stats.ReadingSummary
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
import io.github.fianna76.speedread.ui.news.NewsStory
import io.github.fianna76.speedread.ui.news.NewsstandActions
import io.github.fianna76.speedread.ui.news.NewsstandContent
import io.github.fianna76.speedread.ui.news.NewsstandUiState
import io.github.fianna76.speedread.ui.reader.ReaderActions
import io.github.fianna76.speedread.ui.reader.ReaderContent
import io.github.fianna76.speedread.ui.reader.ReaderUiState
import io.github.fianna76.speedread.ui.settings.SettingsContent
import io.github.fianna76.speedread.ui.sports.SportsActions
import io.github.fianna76.speedread.ui.sports.SportsContent
import io.github.fianna76.speedread.ui.sports.SportsUiState
import io.github.fianna76.speedread.ui.stats.StatsContent
import io.github.fianna76.speedread.ui.theme.SpeedReadTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/**
 * Checks every main screen, at normal and 200 % font size, for the two most common
 * accessibility failures:
 * - something tappable without a spoken label (content description, text or click label);
 * - a touch target smaller than 48 × 48 dp.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class AccessibilityAuditTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = 1_792_252_800_000L
    private val text = Tokenizer.tokenize(listOf("It is a truth universally acknowledged, that a single man must be in want of a wife."))

    private fun audit(fontScale: Float = 1f, content: @Composable () -> Unit) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent { SpeedReadTheme(dynamicColor = false, content = content) }
        compose.waitForIdle()
        val problems = mutableListOf<String>()
        val density = compose.density
        fun visit(node: SemanticsNode) {
            val config = node.config
            val clickable = config.getOrNull(SemanticsActions.OnClick) != null
            if (clickable && config.getOrNull(SemanticsProperties.Disabled) == null && config.getOrNull(SemanticsProperties.InvisibleToUser) == null) {
                val label = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                    ?: config.getOrNull(SemanticsProperties.Text)?.joinToString()
                    ?: config.getOrNull(SemanticsProperties.EditableText)?.text
                    ?: config.getOrNull(SemanticsActions.OnClick)?.label
                if (label.isNullOrBlank()) problems += "Unlabelled tap target at ${node.boundsInRoot}: ${node.config}"
                val touch = node.touchBoundsInRoot
                val width = with(density) { touch.width.toDp() }
                val height = with(density) { touch.height.toDp() }
                // Ignore targets clipped by the screen edge (partly scrolled-off chips and cards).
                val clipped = node.boundsInRoot.width < node.size.width - 1 || node.boundsInRoot.height < node.size.height - 1
                if (!clipped && (width < 47.5.dp || height < 47.5.dp)) {
                    problems += "Small tap target '${label.orEmpty().take(30)}': $width × $height"
                }
            }
            node.children.forEach(::visit)
        }
        visit(compose.onRoot().fetchSemanticsNode())
        assertTrue(problems.distinct().joinToString("\n"), problems.isEmpty())
    }

    private val books = listOf(
        testBook(1, title = "Pride and Prejudice", author = "Jane Austen", totalWords = 120_000, wordsRead = 40_000),
        testBook(2, title = "Moby-Dick", author = "Herman Melville"),
    )

    private fun library() = @Composable {
        LibraryContent(
            state = LibraryUiState(isLoading = false, books = books, collections = listOf(BookCollection(1, "Book club", setOf(1)))),
            onImport = {}, onOpenBook = {}, onOpenSettings = {}, onSortChange = {}, onToggleSelection = {},
            onSelectAll = {}, onClearSelection = {}, onDeleteSelected = {}, onMessageShown = {},
            bottomBar = { TopLevelNavigationBar(TopLevelDestination.LIBRARY) {} },
        )
    }

    private fun newsstand() = @Composable {
        val articles = TestNews.sampleArticles(now)
        NewsstandContent(
            state = NewsstandUiState(
                hasLoaded = true,
                sources = NewsSources.ALL,
                stories = articles.map { NewsStory(it) },
                briefing = articles.map { NewsStory(it) },
                continueReading = articles.take(1),
                lastUpdated = now,
            ),
            now = now,
            actions = NewsstandActions(),
            bottomBar = { TopLevelNavigationBar(TopLevelDestination.NEWS) {} },
        )
    }

    private fun reader() = @Composable {
        ReaderContent(
            ReaderUiState(
                isLoading = false,
                title = "Pride and Prejudice",
                chapters = List(3) { Chapter(it, "Chapter ${it + 1}", "c$it.xhtml", 20, it * 20) },
                chapterIndex = 1,
                totalWords = 60,
                bookmarks = listOf(Bookmark(1, ReadingPosition(1, 0), "It is a truth", 0)),
            ),
            PlayerState(text = text, index = 3),
            ReaderActions(),
        )
    }

    private fun settings() = @Composable {
        SettingsContent(
            preferences = UserPreferences(breakingNewsAlerts = true, morningBriefing = true, speedTraining = true),
            onBack = {}, onWordsPerMinuteChange = {}, onSmartPacingChange = {}, onRampUpChange = {},
            onFontSizeChange = {}, onFontChange = {}, onHighlightChange = {}, onShowContextChange = {},
            onThemeModeChange = {},
        )
    }

    private fun stats() = @Composable {
        val today = LocalDate.of(2026, 10, 18)
        StatsContent(ReadingSummary.of(listOf(ReadingDay(today, 2_000, 400_000)), today), onBack = {})
    }

    private fun sports(tab: Int, state: SportsUiState = TestSports.state()) = @Composable {
        SportsContent(state, TestSports.NOW, ZoneId.of("Europe/Dublin"), SportsActions(), initialTab = tab)
    }

    @Test fun library_normal() = audit(content = library())

    @Test fun library_large_text() = audit(2f, library())

    @Test fun newsstand_normal() = audit(content = newsstand())

    @Test fun newsstand_large_text() = audit(2f, newsstand())

    @Test fun reader_normal() = audit(content = reader())

    @Test fun reader_large_text() = audit(2f, reader())

    @Test fun settings_normal() = audit(content = settings())

    @Test fun settings_large_text() = audit(2f, settings())

    @Test fun stats_normal() = audit(content = stats())

    @Test fun stats_large_text() = audit(2f, stats())

    @Test fun sports_news_normal() = audit(content = sports(0))

    @Test fun sports_calendar_normal() = audit(content = sports(1))

    @Test fun sports_calendar_large_text() = audit(2f, sports(1))

    @Test fun sports_follow_large_text() = audit(2f, sports(0, SportsUiState(hasLoaded = true)))
}
