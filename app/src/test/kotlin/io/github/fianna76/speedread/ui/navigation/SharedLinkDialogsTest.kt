package io.github.fianna76.speedread.ui.navigation

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.speedread.R
import io.github.fianna76.speedread.data.news.ArticleError
import io.github.fianna76.speedread.data.news.SharedLinkReader
import io.github.fianna76.speedread.testing.PHONE
import io.github.fianna76.speedread.ui.theme.SpeedReadTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class SharedLinkDialogsTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun progressFailureAndReadyStates() {
        var state by mutableStateOf<SharedLinkReader.State>(SharedLinkReader.State.Preparing("https://www.blog.example/post"))
        val online = mutableListOf<String>()
        val opened = mutableListOf<Long>()
        var cancelled = 0
        compose.setContent {
            SpeedReadTheme(dynamicColor = false) {
                SharedLinkDialogs(state, onCancel = { cancelled++ }, onOpenOnline = { online += it }, onReady = { opened += it })
            }
        }

        compose.onNodeWithText(context.getString(R.string.shared_link_preparing)).assertIsDisplayed()
        compose.onNodeWithText("blog.example").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.action_cancel)).performClick()
        assertEquals(1, cancelled)

        state = SharedLinkReader.State.Failed("https://www.blog.example/post", ArticleError.NO_TEXT)
        compose.onNodeWithText(context.getString(R.string.shared_link_no_text)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.news_open_online)).performClick()
        assertEquals(listOf("https://www.blog.example/post"), online)

        state = SharedLinkReader.State.Ready(42)
        compose.waitForIdle()
        assertEquals(listOf(42L), opened)
    }
}
