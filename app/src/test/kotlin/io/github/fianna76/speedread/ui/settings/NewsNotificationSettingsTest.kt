package io.github.fianna76.speedread.ui.settings

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.speedread.R
import io.github.fianna76.speedread.data.settings.UserPreferences
import io.github.fianna76.speedread.testing.PHONE
import io.github.fianna76.speedread.ui.theme.SpeedReadTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class NewsNotificationSettingsTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun setContent(preferences: UserPreferences, actions: NewsSettingsActions) = compose.setContent {
        SpeedReadTheme(dynamicColor = false) {
            SettingsContent(
                preferences = preferences,
                onBack = {}, onWordsPerMinuteChange = {}, onSmartPacingChange = {}, onRampUpChange = {},
                onFontSizeChange = {}, onFontChange = {}, onHighlightChange = {}, onShowContextChange = {},
                onThemeModeChange = {},
                newsActions = actions,
            )
        }
    }

    @Test
    fun alertsAndBriefingSwitch() {
        val breaking = mutableListOf<Boolean>()
        val briefing = mutableListOf<Boolean>()
        setContent(
            UserPreferences(),
            NewsSettingsActions(onBreakingAlertsChange = { breaking += it }, onMorningBriefingChange = { briefing += it }),
        )

        compose.onNodeWithText(context.getString(R.string.settings_news_breaking)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.settings_news_briefing)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.settings_news_briefing_time)).assertDoesNotExist()

        assertEquals(listOf(true), breaking)
        assertEquals(listOf(true), briefing)
    }

    @Test
    fun theBriefingTimeCanBeChosenWhileTheBriefingIsOn() {
        val times = mutableListOf<Int>()
        setContent(UserPreferences(morningBriefing = true), NewsSettingsActions(onBriefingTimeChange = { times += it }))

        fun time(h: Int, m: Int) = LocalTime.of(h, m).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        compose.onNodeWithText(time(7, 0)).performScrollTo().performClick()
        compose.onNodeWithText(time(8, 30)).performClick()

        assertEquals(listOf(8 * 60 + 30), times)
    }
}
