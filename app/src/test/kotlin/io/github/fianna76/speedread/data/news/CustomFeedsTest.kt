package io.github.fianna76.speedread.data.news

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.speedread.news.NewsRegion
import io.github.fianna76.speedread.news.NewsSources
import io.github.fianna76.speedread.testing.FakeWebClient
import io.github.fianna76.speedread.testing.MainDispatcherRule
import io.github.fianna76.speedread.testing.TestNews
import io.github.fianna76.speedread.testing.testPreferences
import io.github.fianna76.speedread.ui.settings.AddFeedError
import io.github.fianna76.speedread.ui.settings.SettingsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomFeedsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val feed = TestNews.rss("ditch", "a", "b").replace("<title>ditch</title>", "<title>The Ditch</title><link>https://www.ontheditch.com</link>")
    private val page = "<html><head><link rel=\"alternate\" type=\"application/rss+xml\" href=\"/feed/\"></head><body>Hi</body></html>"

    @Test
    fun `a site address leads to its advertised feed`() = runTest {
        val web = FakeWebClient(mapOf("https://www.ontheditch.com" to page, "https://www.ontheditch.com/feed/" to feed))

        val result = CustomFeedResolver(web).resolve("www.ontheditch.com", NewsRegion.IRELAND) as CustomFeedResolver.Result.Found

        assertEquals("The Ditch", result.source.name)
        assertEquals("https://www.ontheditch.com/feed/", result.source.feedUrl)
        assertEquals("https://www.ontheditch.com", result.source.homepage)
        assertEquals(NewsRegion.IRELAND, result.source.region)
    }

    @Test
    fun `common feed paths are tried when a page advertises none`() = runTest {
        val web = FakeWebClient(mapOf("https://blog.example/" to "<html></html>", "https://blog.example/rss.xml" to feed))
        val result = CustomFeedResolver(web).resolve("https://blog.example/", NewsRegion.WORLD)
        assertEquals("https://blog.example/rss.xml", (result as CustomFeedResolver.Result.Found).source.feedUrl)
    }

    @Test
    fun `built-in feeds, bad addresses, unreachable sites and feedless pages are reported`() = runTest {
        val rte = NewsSources.byId("rte")!!
        val web = FakeWebClient(mapOf(rte.feedUrl to TestNews.rss("rte", "a"), "https://plain.example" to "<html></html>"))
        val resolver = CustomFeedResolver(web)

        assertEquals(CustomFeedResolver.Result.BuiltIn(rte), resolver.resolve(rte.feedUrl, NewsRegion.WORLD))
        assertEquals(CustomFeedResolver.Result.Invalid, resolver.resolve("hello there", NewsRegion.WORLD))
        assertEquals(CustomFeedResolver.Result.Network, resolver.resolve("down.example", NewsRegion.WORLD))
        assertEquals(CustomFeedResolver.Result.NoFeed, resolver.resolve("plain.example", NewsRegion.WORLD))
    }

    @Test
    fun `added feeds are stored, switched on and removable`() = runTest {
        val preferences = testPreferences(tmp.newFolder(), backgroundScope)
        val web = FakeWebClient(mapOf("https://www.ontheditch.com/feed/" to feed, "https://nowhere.example" to "<html></html>"))
        val viewModel = SettingsViewModel(preferences, CustomFeedResolver(web))

        viewModel.addFeed("https://www.ontheditch.com/feed/", NewsRegion.IRELAND)
        assertEquals("The Ditch", viewModel.addFeed.first { it.added != null }.added)
        val prefs = preferences.preferences.first { it.customNewsSources.isNotEmpty() }
        val source = prefs.customNewsSources.single()
        assertTrue(source in prefs.enabledNewsSources)
        // Grouped with the Irish sources, before Europe and the world.
        assertTrue(prefs.enabledNewsSources.indexOf(source) < prefs.enabledNewsSources.indexOfFirst { it.region == NewsRegion.EUROPE })
        assertEquals(source, prefs.newsSource(source.id))

        viewModel.resetAddFeed()
        viewModel.addFeed("nowhere.example", NewsRegion.WORLD)
        assertEquals(AddFeedError.NO_FEED, viewModel.addFeed.first { it.error != null }.error)

        viewModel.removeFeed(source.id)
        assertTrue(preferences.preferences.first { it.customNewsSources.isEmpty() }.enabledNewsSources.none { it.id == source.id })
    }
}
