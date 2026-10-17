package io.github.fianna76.speedread.data.settings

import io.github.fianna76.speedread.news.NewsRegion
import io.github.fianna76.speedread.news.NewsSources
import io.github.fianna76.speedread.rsvp.PlaybackSettings
import io.github.fianna76.speedread.testing.testPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UserPreferencesRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `fresh installs get the defaults`() = runTest {
        val repository = testPreferences(tmp.root, backgroundScope)
        assertEquals(UserPreferences(), repository.preferences.first())
    }

    @Test
    fun `stores every setting`() = runTest {
        val repository = testPreferences(tmp.root, backgroundScope)

        repository.setWordsPerMinute(475)
        repository.setSmartPacing(false)
        repository.setRampUp(false)
        repository.setFontSize(56)
        repository.setFont(ReaderFont.SERIF)
        repository.setHighlightFocusLetter(false)
        repository.setShowContextWhenPaused(false)
        repository.setThemeMode(ThemeMode.DARK)
        repository.setLibrarySort(LibrarySort.AUTHOR)

        assertEquals(
            UserPreferences(
                playback = PlaybackSettings(wordsPerMinute = 475, smartPacing = false, rampUp = false),
                display = ReaderDisplay(
                    fontSizeSp = 56,
                    font = ReaderFont.SERIF,
                    highlightFocusLetter = false,
                    showContextWhenPaused = false,
                ),
                themeMode = ThemeMode.DARK,
                librarySort = LibrarySort.AUTHOR,
            ),
            repository.preferences.first(),
        )
    }

    @Test
    fun `news sources start at their defaults and can be switched off and on`() = runTest {
        val repository = testPreferences(tmp.root, backgroundScope)
        val defaults = NewsSources.ALL.filter { it.enabledByDefault }
        assertEquals(defaults, repository.preferences.first().enabledNewsSources)
        assertEquals("rte", defaults.first().id) // Irish news first.

        repository.setNewsSourceEnabled("bbc", false) // On by default.
        repository.setNewsSourceEnabled("tuairisc", true) // Off by default.
        repository.setNewsSourceEnabled("dw", false)
        repository.setNewsSourceEnabled("dw", true)

        val prefs = repository.preferences.first()
        assertEquals(
            NewsSources.ALL.filter { (it.enabledByDefault && it.id != "bbc") || it.id == "tuairisc" },
            prefs.enabledNewsSources,
        )
    }

    @Test
    fun `a whole region can be switched on and off, and newsstand options are stored`() = runTest {
        val repository = testPreferences(tmp.root, backgroundScope)
        val europe = NewsSources.ALL.filter { it.region == NewsRegion.EUROPE }.map { it.id }

        repository.setNewsSourcesEnabled(europe, true)
        assertTrue(repository.preferences.first().enabledNewsSources.map { it.id }.containsAll(europe))
        repository.setNewsSourcesEnabled(europe, false)
        assertTrue(repository.preferences.first().enabledNewsSources.none { it.id in europe })

        repository.setNewsImages(NewsImages.NEVER)
        repository.setAutoContinueNews(false)
        repository.setBackgroundNews(true)
        val prefs = repository.preferences.first()
        assertEquals(NewsImages.NEVER, prefs.newsImages)
        assertEquals(false, prefs.autoContinueNews)
        assertEquals(true, prefs.backgroundNews)
    }

    @Test
    fun `out-of-range values are clamped`() = runTest {
        val repository = testPreferences(tmp.root, backgroundScope)

        repository.setWordsPerMinute(10)
        repository.setFontSize(500)

        val preferences = repository.preferences.first()
        assertEquals(100, preferences.playback.wordsPerMinute)
        assertEquals(ReaderDisplay.MAX_FONT_SIZE_SP, preferences.display.fontSizeSp)
    }
}
