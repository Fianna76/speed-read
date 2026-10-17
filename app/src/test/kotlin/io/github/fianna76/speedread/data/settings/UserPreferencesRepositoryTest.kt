package io.github.fianna76.speedread.data.settings

import io.github.fianna76.speedread.news.NewsSources
import io.github.fianna76.speedread.rsvp.PlaybackSettings
import io.github.fianna76.speedread.testing.testPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    fun `news sources can be switched off and on`() = runTest {
        val repository = testPreferences(tmp.root, backgroundScope)
        assertEquals(NewsSources.ALL, repository.preferences.first().enabledNewsSources)

        repository.setNewsSourceEnabled("bbc", false)
        repository.setNewsSourceEnabled("dw", false)
        repository.setNewsSourceEnabled("bbc", true)

        val prefs = repository.preferences.first()
        assertEquals(setOf("dw"), prefs.disabledNewsSources)
        assertEquals(NewsSources.ALL.filterNot { it.id == "dw" }, prefs.enabledNewsSources)
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
