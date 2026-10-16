package io.github.fianna76.speedread

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.speedread.epub.testing.TestEpubs
import io.github.fianna76.speedread.testing.PHONE
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * End-to-end flow on the real app graph (Room, DataStore, files): import an EPUB, open it,
 * read a few words, go back and find the progress saved.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class AppFlowTest {

    @get:Rule(order = 0)
    val tmp = TemporaryFolder()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun waitFor(matcher: SemanticsMatcher) =
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForAbsence(matcher: SemanticsMatcher) =
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(matcher).fetchSemanticsNodes().isEmpty() }

    @Test
    fun importReadAndResume() {
        val app = context as SpeedReadApplication
        val epub = TestEpubs.simple(
            tmp.newFile("flow.epub"),
            title = "Flow Book",
            body = "<p>Alpha beta gamma. Delta epsilon zeta.</p><p>Eta theta iota kappa.</p>",
        )
        app.container.importCoordinator.enqueue(listOf(Uri.fromFile(epub)))

        // The imported book appears in the library, unread.
        waitFor(hasText("Flow Book"))
        compose.onNodeWithText(context.getString(R.string.book_new)).assertExists()

        // Opening it shows the first word, paused.
        compose.onNodeWithText("Flow Book").performClick()
        waitFor(hasContentDescription("Alpha"))

        // Play for a while: the player is driven by the main looper's (virtual) clock.
        compose.onNodeWithContentDescription(context.getString(R.string.reader_play)).performClick()
        ShadowLooper.idleMainLooper(2_000, TimeUnit.MILLISECONDS)
        waitForAbsence(hasContentDescription("Alpha"))
        compose.onNodeWithContentDescription(context.getString(R.string.reader_pause)).performClick()

        // Back in the library the book shows progress instead of "New".
        compose.onNodeWithContentDescription(context.getString(R.string.action_back)).performClick()
        waitFor(hasText("Flow Book"))
        waitForAbsence(hasText(context.getString(R.string.book_new)))
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
