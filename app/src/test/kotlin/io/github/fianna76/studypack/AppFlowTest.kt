package io.github.fianna76.studypack

import android.content.Context
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.studypack.testing.PHONE
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * End to end on the real app graph (Room, files, import): load the sample pack, open its module,
 * start a quiz, answer a question in tutor mode, end early and see the results, then find the
 * score on the library and the answer in the history.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class AppFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun waitFor(matcher: SemanticsMatcher) =
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun sampleQuizFlow() {
        val container = (context as StudyPackApplication).container

        // Empty library, then the sample pack.
        waitFor(hasText(string(R.string.library_empty_title)))
        compose.onNodeWithText(string(R.string.library_load_sample)).performClick()
        // The import summary dialog (its OK button) appears once both files are in.
        waitFor(hasText(string(R.string.ok)))
        compose.onNodeWithText(string(R.string.ok)).performClick()
        waitFor(hasText("DEMO101"))

        // Module screen: the notes attached automatically, and the pack is listed.
        compose.onNodeWithText("Sample module: circuit basics").performClick()
        waitFor(hasText("Circuit Basics: Overview"))
        compose.onNodeWithText("Circuit Basics (sample pack)").assertExists()

        // Setup: 19 questions with one 4-instance variant group = 16 slots, all of them in a bench demo.
        compose.onNodeWithText(string(R.string.module_quiz)).performClick()
        waitFor(hasText(string(R.string.setup_start)))
        waitFor(hasText("16 questions from 4 topics"))
        compose.onNodeWithText(string(R.string.setup_start)).performClick()

        // Tutor mode: answer A, see feedback and the explanation.
        waitFor(hasText(string(R.string.quiz_progress, 1, 16)))
        compose.onNode(hasContentDescription(string(R.string.quiz_option, "A", ""), substring = true)).performClick()
        waitFor(hasText(string(R.string.quiz_explanation)))
        compose.onNodeWithText(string(R.string.quiz_next)).performClick()
        waitFor(hasText(string(R.string.quiz_progress, 2, 16)))

        // End early: results for the one answered question.
        compose.onNodeWithText(string(R.string.quiz_end_early)).performClick()
        compose.onNodeWithText(string(R.string.quiz_end_confirm)).performClick()
        waitFor(hasText(string(R.string.results_title)))
        waitFor(hasText(string(R.string.results_answered_of, 1, 16)))

        // History and the session summary are written in the background.
        compose.waitUntil(TIMEOUT_MS) {
            runBlocking {
                val seen = container.repository.history(listOf("demo101-circuit-basics")).values.sumOf { it.seen }
                seen == 1 && container.repository.observeModules().first().single().lastPercent != null
            }
        }
        val percent = runBlocking { container.repository.observeModules().first().single().lastPercent }
        assertTrue(percent == 0 || percent == 100)
    }

    @Test
    fun deletePackThenModule() {
        val container = (context as StudyPackApplication).container
        waitFor(hasText(string(R.string.library_empty_title)))
        compose.onNodeWithText(string(R.string.library_load_sample)).performClick()
        waitFor(hasText(string(R.string.ok)))
        compose.onNodeWithText(string(R.string.ok)).performClick()
        waitFor(hasText("DEMO101"))

        // Delete the pack from its card's menu; the notes keep the module alive.
        compose.onNodeWithText("Sample module: circuit basics").performClick()
        val pack = "Circuit Basics (sample pack)"
        waitFor(hasText(pack))
        compose.onNode(hasContentDescription(string(R.string.more_options_for, pack))).performClick()
        compose.onNodeWithText(string(R.string.delete)).performClick()
        waitFor(hasText(string(R.string.delete_pack_title, pack)))
        compose.onNodeWithText(string(R.string.delete)).performClick()
        waitFor(hasText(string(R.string.module_no_packs)))
        compose.onNodeWithText("Circuit Basics: Overview").assertExists()

        // Back in the library, delete the whole module from its card's menu.
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitFor(hasContentDescription(string(R.string.more_options_for, "DEMO101")))
        compose.onNode(hasContentDescription(string(R.string.more_options_for, "DEMO101"))).performClick()
        compose.onNodeWithText(string(R.string.delete_module)).performClick()
        waitFor(hasText(string(R.string.delete_module_title, "DEMO101")))
        compose.onNodeWithText(string(R.string.delete)).performClick()
        waitFor(hasText(string(R.string.library_empty_title)))

        runBlocking {
            assertTrue(container.repository.modules().isEmpty())
            val notesDir = java.io.File(context.filesDir, "notes")
            assertTrue(notesDir.listFiles().isNullOrEmpty())
        }
    }

    @Test
    fun deleteModuleFromItsScreen() {
        waitFor(hasText(string(R.string.library_empty_title)))
        compose.onNodeWithText(string(R.string.library_load_sample)).performClick()
        waitFor(hasText(string(R.string.ok)))
        compose.onNodeWithText(string(R.string.ok)).performClick()
        compose.onNodeWithText("Sample module: circuit basics").performClick()
        waitFor(hasText("Circuit Basics (sample pack)"))
        compose.onNode(hasContentDescription(string(R.string.more_options))).performClick()
        compose.onNodeWithText(string(R.string.delete_module)).performClick()
        // The dialog says what goes.
        waitFor(hasText("1 pack and 1 notes PDF", substring = true))
        compose.onNodeWithText(string(R.string.delete)).performClick()
        // The module screen closes by itself, back to an empty library.
        waitFor(hasText(string(R.string.library_empty_title)))
    }

    private companion object {
        const val TIMEOUT_MS = 20_000L
    }
}
