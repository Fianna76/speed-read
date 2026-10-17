package io.github.fianna76.speedread.ui.news

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.speedread.R
import io.github.fianna76.speedread.data.news.ArticleError
import io.github.fianna76.speedread.news.FeedItem
import io.github.fianna76.speedread.news.NewsRegion
import io.github.fianna76.speedread.news.NewsSource
import io.github.fianna76.speedread.news.NewsSources
import io.github.fianna76.speedread.testing.PHONE
import io.github.fianna76.speedread.testing.TestNews
import io.github.fianna76.speedread.ui.theme.SpeedReadTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class NewsstandContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = 1_792_252_800_000L
    private val articles = TestNews.sampleArticles(now)
    private val state = NewsstandUiState(
        hasLoaded = true,
        sources = NewsSources.ALL,
        stories = articles.map { NewsStory(it) },
        lastUpdated = now,
    )

    private fun setContent(state: NewsstandUiState, actions: NewsstandActions = NewsstandActions()) = compose.setContent {
        SpeedReadTheme(dynamicColor = false) { NewsstandContent(state = state, now = now, actions = actions) }
    }

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    @Test
    fun cardsPreviewTheStoryAndOfferEveryAction() {
        val read = mutableListOf<FeedItem>()
        val online = mutableListOf<FeedItem>()
        val saved = mutableListOf<Pair<FeedItem, Boolean>>()
        setContent(
            state,
            NewsstandActions(onReadInApp = { read += it }, onOpenOnline = { online += it }, onSetSaved = { item, s -> saved += item to s }),
        )

        compose.onNodeWithText(articles[0].item.title).assertIsDisplayed()
        compose.onNodeWithText(articles[0].item.summary).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.news_updated_just_now)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.news_progress, 40)).assertExists()

        compose.onAllNodesWithText(string(R.string.news_speed_read)).onFirst().performClick()
        compose.onAllNodesWithContentDescription(string(R.string.news_open_online)).onFirst().performClick()
        compose.onAllNodesWithContentDescription(string(R.string.news_save)).onFirst().performClick()

        assertEquals(listOf(articles[0].item), read)
        assertEquals(listOf(articles[0].item), online)
        assertEquals(listOf(articles[0].item to true), saved)
    }

    @Test
    fun theMoreMenuShares() {
        val shared = mutableListOf<FeedItem>()
        setContent(state, NewsstandActions(onShare = { shared += it }))

        compose.onAllNodesWithContentDescription(string(R.string.news_story_options)).onFirst().performClick()
        compose.onNodeWithText(string(R.string.news_share)).performClick()

        assertEquals(listOf(articles[0].item), shared)
    }

    @Test
    fun theMoreMenuHidesTheOutlet() {
        val hidden = mutableListOf<NewsSource>()
        setContent(state, NewsstandActions(onHideSource = { hidden += it }))

        compose.onAllNodesWithContentDescription(string(R.string.news_story_options)).onFirst().performClick()
        compose.onNodeWithText(string(R.string.news_hide_source, "BBC News")).performClick()

        assertEquals(listOf("bbc"), hidden.map { it.id })
    }

    @Test
    fun readSavedAndGroupedStoriesAreLabelled() {
        val lead = articles[0].copy(isRead = true, isSaved = true)
        val versions = listOf(articles[1], articles[2], articles[1].copy(source = NewsSources.byId("npr")!!))
        setContent(state.copy(stories = listOf(NewsStory(lead, versions))))

        compose.onNodeWithText(string(R.string.news_read), substring = true).assertExists()
        compose.onNodeWithContentDescription(string(R.string.news_unsave)).assertExists()
        compose.onNodeWithText(string(R.string.news_also_covered_more, "Al Jazeera, DW", 1)).assertIsDisplayed()
    }

    @Test
    fun startedArticlesOfferToContinue() {
        setContent(state.copy(continueReading = listOf(articles[1])))

        compose.onNodeWithText(string(R.string.news_continue_reading)).assertIsDisplayed()
        compose.onAllNodesWithText(string(R.string.news_continue)).onFirst().assertExists()
    }

    @Test
    fun tappingACardOpensItsPreviewWithOtherVersions() {
        val versions = listOf(articles[2])
        setContent(state.copy(stories = listOf(NewsStory(articles[0], versions))))

        compose.onNodeWithText(articles[0].item.title).performClick()

        // The sheet shows the outlet's address and lists the other versions, which can be opened.
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("bbc.example")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(string(R.string.news_other_versions)).assertExists()
        compose.onNodeWithText(articles[2].item.title).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("dw.example")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun selectingARegionTheReadingListOrASourceFilters() {
        val regions = mutableListOf<NewsRegion?>()
        val sources = mutableListOf<String?>()
        val saved = mutableListOf<Boolean>()
        setContent(
            state.copy(savedCount = 2),
            NewsstandActions(onSelectRegion = { regions += it }, onSelectSource = { sources += it }, onShowSaved = { saved += it }),
        )

        compose.onNodeWithText(string(R.string.news_region_ireland)).performClick()
        compose.onNodeWithText(string(R.string.news_all_sources)).performClick()
        compose.onNodeWithText("RTÉ News").performClick()
        compose.onNodeWithText(string(R.string.news_saved_count, 2)).performClick()

        assertEquals(listOf(NewsRegion.IRELAND, null), regions)
        assertEquals(listOf("rte"), sources)
        assertEquals(listOf(true), saved)
    }

    @Test
    fun aSelectedRegionOffersOnlyItsOutlets() {
        setContent(state.copy(selectedRegion = NewsRegion.IRELAND))

        compose.onNodeWithText("RTÉ News").assertExists()
        compose.onNodeWithText("Al Jazeera").assertDoesNotExist()
    }

    @Test
    fun searchSendsTheQueryAndExplainsAnEmptyResult() {
        val queries = mutableListOf<String>()
        var current by mutableStateOf(state)
        compose.setContent {
            SpeedReadTheme(dynamicColor = false) {
                NewsstandContent(state = current, now = now, actions = NewsstandActions(onQueryChange = { queries += it }))
            }
        }

        compose.onNodeWithContentDescription(string(R.string.news_search)).performClick()
        compose.onNodeWithText(string(R.string.news_search_hint)).performTextInput("budget")
        assertEquals("budget", queries.last())

        current = state.copy(query = "budget", stories = emptyList())
        compose.onNodeWithText(string(R.string.news_search_empty_title, "budget")).assertIsDisplayed()

        compose.onNodeWithContentDescription(string(R.string.news_close_search)).performClick()
        assertEquals("", queries.last())
    }

    @Test
    fun theReadingListExplainsItselfWhenEmpty() {
        setContent(state.copy(showingSaved = true, stories = emptyList()))

        compose.onNodeWithText(string(R.string.news_saved_empty_title)).assertIsDisplayed()
    }

    @Test
    fun hidingReadStoriesCanLeaveNothingToShow() {
        var hideRead: Boolean? = null
        setContent(state.copy(hideRead = true, stories = emptyList()), NewsstandActions(onHideReadChange = { hideRead = it }))

        compose.onNodeWithText(string(R.string.news_all_read_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.news_show_read)).performClick()
        assertEquals(false, hideRead)
    }

    @Test
    fun whileAnArticleIsPreparedOtherDownloadsWait() {
        setContent(state.copy(preparingUrl = articles[0].item.canonicalUrl))

        compose.onNodeWithText(string(R.string.news_preparing)).assertIsDisplayed()
        compose.onAllNodesWithText(string(R.string.news_speed_read)).onFirst().assertIsNotEnabled()
    }

    @Test
    fun failuresOfferTheOnlineVersion() {
        val online = mutableListOf<FeedItem>()
        val shown = mutableListOf<Long>()
        val message = NewsMessage.ArticleFailed(id = 7, item = articles[1].item, error = ArticleError.PAYWALLED)
        setContent(state.copy(message = message), NewsstandActions(onOpenOnline = { online += it }, onMessageShown = { shown += it }))

        compose.onNodeWithText(string(R.string.news_error_paywalled)).assertIsDisplayed()
        // The cards' "Open online" are icons; the only text button with that label is the snackbar's.
        compose.onNodeWithText(string(R.string.news_open_online)).performClick()

        compose.waitUntil(5_000) { shown.isNotEmpty() }
        assertEquals(listOf(articles[1].item), online)
        assertEquals(listOf(7L), shown)
    }

    @Test
    fun aHiddenOutletCanBeRestored() {
        val restored = mutableListOf<NewsSource>()
        val dw = NewsSources.byId("dw")!!
        setContent(state.copy(message = NewsMessage.SourceHidden(3, dw)), NewsstandActions(onUndoHideSource = { restored += it }))

        compose.onNodeWithText(string(R.string.news_undo)).performClick()

        compose.waitUntil(5_000) { restored.isNotEmpty() }
        assertEquals(listOf(dw), restored)
    }

    @Test
    fun aFewUnreachableSourcesAreNamedAndManyAreCounted() {
        val failing = NewsSources.ALL.take(5)
        var current by mutableStateOf(state.copy(failedSources = failing.take(2)))
        compose.setContent { SpeedReadTheme(dynamicColor = false) { NewsstandContent(state = current, now = now, actions = NewsstandActions()) } }

        compose.onNodeWithText(string(R.string.news_sources_failed, "RTÉ News, TheJournal.ie")).assertIsDisplayed()

        current = state.copy(failedSources = failing)
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.news_many_sources_failed, 5, 5)).assertIsDisplayed()
    }

    @Test
    fun emptyStatesExplainWhatToDo() {
        var settingsOpened = false
        setContent(NewsstandUiState(hasLoaded = true), NewsstandActions(onOpenSettings = { settingsOpened = true }))

        compose.onNodeWithText(string(R.string.news_no_sources_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.news_choose_sources)).performClick()
        assertEquals(true, settingsOpened)
    }

    @Test
    fun offlineWithNothingCachedOffersARetry() {
        var refreshed = 0
        setContent(
            NewsstandUiState(hasLoaded = true, sources = NewsSources.ALL, failedSources = NewsSources.ALL),
            NewsstandActions(onRefresh = { refreshed++ }),
        )

        compose.onNodeWithText(string(R.string.news_failed_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.news_retry)).performClick()
        assertEquals(1, refreshed)
    }

    @Test
    fun aRequestedStoryIsPreviewedOnceListed() {
        var current by mutableStateOf(state.copy(stories = emptyList()))
        var handled = 0
        compose.setContent {
            SpeedReadTheme(dynamicColor = false) {
                NewsstandContent(
                    state = current,
                    now = now,
                    actions = NewsstandActions(),
                    previewRequest = articles[2].item.canonicalUrl,
                    onPreviewRequestHandled = { handled++ },
                )
            }
        }
        assertEquals(0, handled)

        current = state // The headlines arrive.

        compose.waitUntil(5_000) { compose.onAllNodes(hasText("dw.example")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, handled)
    }
}
