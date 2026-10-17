package io.github.fianna76.speedread.ui.news

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.speedread.R
import io.github.fianna76.speedread.data.news.ArticleError
import io.github.fianna76.speedread.news.FeedItem
import io.github.fianna76.speedread.news.NewsRegion
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
    private val state = NewsstandUiState(hasLoaded = true, sources = NewsSources.ALL, articles = articles, lastUpdated = now)

    private fun setContent(state: NewsstandUiState, actions: NewsstandActions = NewsstandActions()) = compose.setContent {
        SpeedReadTheme(dynamicColor = false) { NewsstandContent(state = state, now = now, actions = actions) }
    }

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    @Test
    fun cardsPreviewTheStoryAndOfferBothWaysToRead() {
        val read = mutableListOf<FeedItem>()
        val online = mutableListOf<FeedItem>()
        setContent(state, NewsstandActions(onReadInApp = { read += it }, onOpenOnline = { online += it }))

        compose.onNodeWithText(articles[0].item.title).assertIsDisplayed()
        compose.onNodeWithText(articles[0].item.summary).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.news_updated_just_now)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.news_progress, 40)).assertExists()

        compose.onAllNodesWithText(string(R.string.news_speed_read)).onFirst().performClick()
        compose.onAllNodesWithText(string(R.string.news_open_online)).onFirst().performClick()

        assertEquals(listOf(articles[0].item), read)
        assertEquals(listOf(articles[0].item), online)
    }

    @Test
    fun startedArticlesOfferToContinue() {
        setContent(state)

        compose.onNodeWithText(string(R.string.news_continue)).assertExists()
    }

    @Test
    fun tappingACardOpensItsPreview() {
        val read = mutableListOf<FeedItem>()
        setContent(state, NewsstandActions(onReadInApp = { read += it }))

        compose.onNodeWithText(articles[2].item.title).performClick()

        // The sheet repeats the headline with the outlet's address and its own buttons.
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("dw.example")).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText(articles[2].item.title).fetchSemanticsNodes().let { assertEquals(2, it.size) }
    }

    @Test
    fun selectingARegionOrASourceFilters() {
        val regions = mutableListOf<NewsRegion?>()
        val sources = mutableListOf<String?>()
        setContent(state, NewsstandActions(onSelectRegion = { regions += it }, onSelectSource = { sources += it }))

        compose.onNodeWithText(string(R.string.news_region_ireland)).performClick()
        compose.onNodeWithText(string(R.string.news_all_sources)).performClick()
        compose.onNodeWithText("RTÉ News").performClick()

        assertEquals(listOf(NewsRegion.IRELAND, null), regions)
        assertEquals(listOf("rte"), sources)
    }

    @Test
    fun aSelectedRegionOffersOnlyItsOutlets() {
        setContent(state.copy(selectedRegion = NewsRegion.IRELAND))

        compose.onNodeWithText("RTÉ News").assertExists()
        compose.onNodeWithText("Al Jazeera").assertDoesNotExist()
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
        val message = NewsMessage(id = 7, item = articles[1].item, error = ArticleError.NO_TEXT)
        setContent(state.copy(message = message), NewsstandActions(onOpenOnline = { online += it }, onMessageShown = { shown += it }))

        compose.onNodeWithText(string(R.string.news_error_no_text)).assertIsDisplayed()
        // The snackbar's action is the last "Open online" in the tree, after the cards' buttons.
        compose.onAllNodesWithText(string(R.string.news_open_online)).let { nodes ->
            nodes[nodes.fetchSemanticsNodes().lastIndex].performClick()
        }

        compose.waitUntil(5_000) { shown.isNotEmpty() }
        assertEquals(listOf(articles[1].item), online)
        assertEquals(listOf(7L), shown)
    }

    @Test
    fun aFewUnreachableSourcesAreNamedAndManyAreCounted() {
        val failing = NewsSources.ALL.take(5)
        var current by mutableStateOf(state.copy(failedSources = failing.take(2)))
        compose.setContent { SpeedReadTheme(dynamicColor = false) { NewsstandContent(state = current, now = now, actions = NewsstandActions()) } }

        compose.onNodeWithText(string(R.string.news_sources_failed, "RT\u00C9 News, TheJournal.ie")).assertIsDisplayed()

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
}
