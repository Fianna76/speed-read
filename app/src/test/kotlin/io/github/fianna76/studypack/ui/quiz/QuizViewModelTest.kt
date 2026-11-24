package io.github.fianna76.studypack.ui.quiz

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.studypack.quiz.Answers
import io.github.fianna76.studypack.quiz.PackParser
import io.github.fianna76.studypack.quiz.QuestionKey
import io.github.fianna76.studypack.quiz.QuizLength
import io.github.fianna76.studypack.testing.MainDispatcherRule
import io.github.fianna76.studypack.testing.TestGraph
import io.github.fianna76.studypack.testing.resource
import io.github.fianna76.studypack.ui.navigation.QuizRoute
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuizViewModelTest {

    @get:Rule(order = 0)
    val tmp = TemporaryFolder()

    @get:Rule(order = 1)
    val main = MainDispatcherRule()

    private val graph by lazy { TestGraph(tmp.newFolder("app")) }

    private val packId = "ee5105-ecg-amp"

    @Before
    fun setUp() = runBlocking {
        graph.repository.savePack(checkNotNull(PackParser.parse(resource("EE5105-ecg-amp.studypack.json")).pack))
    }

    @After
    fun tearDown() = graph.close()

    private fun route(length: QuizLength = QuizLength.BENCH_DEMO, examMode: Boolean = false, seed: Long = 42) =
        QuizRoute(moduleCode = "EE5105", packIds = listOf(packId), length = length.name, examMode = examMode, seed = seed)

    private fun viewModel(route: QuizRoute, saved: SavedStateHandle = SavedStateHandle()) =
        QuizViewModel(route, saved, graph.repository, graph.scope, clock = { graph.now })

    private fun QuizViewModel.loaded(): QuizUiState = runBlocking { withTimeout(10_000) { uiState.first { !it.isLoading } } }

    private fun awaitHistory(predicate: (Map<QuestionKey, io.github.fianna76.studypack.quiz.QuestionHistory>) -> Boolean) = runBlocking {
        withTimeout(10_000) { graph.repository.observeHistory(listOf(packId)).first(predicate) }
    }

    @Test
    fun tutorModeLocksTheAnswerAndRecordsIt() {
        val vm = viewModel(route())
        val state = vm.loaded()
        assertEquals(25, state.questions.size)
        assertEquals(10, state.questions.map { it.question.topicId }.toSet().size)
        val first = state.questions.first()
        val wrong = (first.correctIndex + 1) % 4
        vm.choose(wrong)
        vm.choose(first.correctIndex) // ignored: tutor mode locks the first tap
        assertEquals(wrong, vm.uiState.value.currentAnswer)
        assertTrue(vm.uiState.value.isRevealed)
        assertEquals(0, vm.uiState.value.correctCount)
        val history = awaitHistory { it.isNotEmpty() }
        assertEquals(1, history[first.question.key]?.wrong)
        vm.next()
        assertEquals(1, vm.uiState.value.index)
    }

    @Test
    fun examModeAllowsChangesUntilNext() {
        val vm = viewModel(route(examMode = true))
        val first = vm.loaded().questions.first()
        vm.choose(0)
        vm.choose(first.correctIndex)
        assertEquals(first.correctIndex, vm.uiState.value.selection)
        assertNull(vm.uiState.value.currentAnswer)
        assertFalse(vm.uiState.value.isRevealed)
        vm.next()
        assertEquals(first.correctIndex, vm.uiState.value.answers[0])
        assertEquals(1, vm.uiState.value.index)
        assertNull(vm.uiState.value.selection)
        val history = awaitHistory { it.isNotEmpty() }
        assertEquals(1, history[first.question.key]?.seen)
    }

    @Test
    fun endingEarlyScoresOnlyAnsweredQuestionsAndSavesTheSession() = runBlocking {
        val vm = viewModel(route(length = QuizLength.WARM_UP))
        val questions = vm.loaded().questions
        vm.choose(questions[0].correctIndex)
        vm.next()
        vm.choose((questions[1].correctIndex + 1) % 4)
        vm.next()
        vm.endEarly()
        val state = vm.uiState.value
        assertTrue(state.finished)
        val score = checkNotNull(state.score)
        assertEquals(12, score.total)
        assertEquals(2, score.answered)
        assertEquals(1, score.correct)
        assertEquals(50, score.percent)
        val module = withTimeout(10_000) { graph.repository.observeModules().first { it.single().lastPercent != null } }
        assertEquals(50, module.single().lastPercent)

        val retry = checkNotNull(vm.retryRoute())
        assertEquals(listOf(questions[1].question.key.encode()), retry.retryKeys)
        assertFalse(retry.examMode)
        val retryVm = viewModel(retry)
        val retryState = retryVm.loaded()
        assertEquals(1, retryState.questions.size)
        assertTrue(retryState.isRetry)
    }

    @Test
    fun sessionIsRestoredFromSavedState() {
        val saved = SavedStateHandle()
        val vm = viewModel(route(), saved)
        val questions = vm.loaded().questions
        vm.choose(questions[0].correctIndex)
        vm.next()
        vm.choose(questions[1].correctIndex)

        // A new ViewModel with a different seed would build a different session; the saved state wins.
        val restored = viewModel(route(seed = 7), saved).loaded()
        assertEquals(questions, restored.questions)
        assertEquals(1, restored.index)
        assertEquals(questions[0].correctIndex, restored.answers[0])
        assertEquals(questions[1].correctIndex, restored.answers[1])
    }

    @Test
    fun sameSeedGivesTheSameSession() {
        val a = viewModel(route(seed = 99)).loaded().questions
        val b = viewModel(route(seed = 99)).loaded().questions
        val c = viewModel(route(seed = 100)).loaded().questions
        assertEquals(a, b)
        assertTrue(a != c)
    }

    @Test
    fun weakSpotsUseHistory() = runBlocking {
        val missed = listOf(QuestionKey(packId, "cmrr-001"), QuestionKey(packId, "noise-001"))
        missed.forEach { graph.repository.recordAnswer(it, correct = false, tutorMode = true) }
        val state = viewModel(route(length = QuizLength.WEAK_SPOTS)).loaded()
        assertEquals(missed.toSet(), state.questions.map { it.question.key }.toSet())
    }

    @Test
    fun dontKnowInTutorModeShowsTheAnswerAndMarksForRevision() {
        val vm = viewModel(route())
        val first = vm.loaded().questions.first()
        assertTrue(vm.uiState.value.canSkip)
        vm.skip()
        val state = vm.uiState.value
        assertEquals(Answers.SKIPPED, state.currentAnswer)
        assertTrue("Tutor mode reveals the answer", state.isRevealed)
        assertTrue(state.canGoNext)
        assertFalse(state.canSkip)
        assertEquals(0, state.correctCount)
        assertEquals(1, state.skippedCount)
        // Choosing an option afterwards changes nothing.
        vm.choose(first.correctIndex)
        assertEquals(Answers.SKIPPED, vm.uiState.value.currentAnswer)
        val history = awaitHistory { it.isNotEmpty() }.getValue(first.question.key)
        assertEquals(1, history.skipped)
        assertEquals("A skip is not a wrong answer", 0, history.wrong)
        assertEquals(1, history.seen)
    }

    @Test
    fun dontKnowInExamModeMovesOn() {
        val vm = viewModel(route(length = QuizLength.WARM_UP, examMode = true))
        val questions = vm.loaded().questions
        vm.choose(0) // a pick that is then abandoned
        vm.skip()
        assertEquals(1, vm.uiState.value.index)
        assertEquals(Answers.SKIPPED, vm.uiState.value.answers[0])
        assertNull(vm.uiState.value.selection)
        assertFalse(vm.uiState.value.isRevealed)
        // Skipping the last question finishes the quiz.
        repeat(questions.size - 2) { vm.choose(questions[it + 1].correctIndex); vm.next() }
        vm.skip()
        assertTrue(vm.uiState.value.finished)
        assertEquals(2, vm.uiState.value.score!!.skipped)
    }

    @Test
    fun resultsKeepSkippedApartFromWrong() {
        val vm = viewModel(route(length = QuizLength.WARM_UP))
        val questions = vm.loaded().questions
        vm.choose(questions[0].correctIndex)
        vm.next()
        vm.choose((questions[1].correctIndex + 1) % 4)
        vm.next()
        vm.skip()
        vm.next()
        vm.endEarly()
        val state = vm.uiState.value
        val score = checkNotNull(state.score)
        assertEquals(3, score.answered)
        assertEquals(1, score.correct)
        assertEquals(1, score.wrong)
        assertEquals(1, score.skipped)
        assertEquals(listOf(1), state.missed)
        assertEquals(listOf(2), state.skipped)
        assertEquals(listOf(questions[1].question.key.encode()), vm.retryRoute()!!.retryKeys)
        assertEquals(listOf(questions[2].question.key.encode()), vm.retrySkippedRoute()!!.retryKeys)
    }

    @Test
    fun skippedAnswerSurvivesRestore() {
        val saved = SavedStateHandle()
        val vm = viewModel(route(), saved)
        vm.loaded()
        vm.skip()
        val restored = viewModel(route(), saved).loaded()
        assertEquals(Answers.SKIPPED, restored.answers[0])
        assertTrue(restored.isRevealed)
    }

    @Test
    fun revisionQuizAsksTheMarkedQuestions() = runBlocking {
        val marked = listOf(QuestionKey(packId, "cmrr-001"), QuestionKey(packId, "noise-001"), QuestionKey(packId, "stage-1-001"))
        marked.forEach { graph.repository.recordSkip(it) }
        graph.repository.recordAnswer(QuestionKey(packId, "cmrr-002"), correct = false, tutorMode = true) // a weak spot, not marked
        // Answered right since: no longer marked.
        graph.repository.recordAnswer(marked.last(), correct = true, tutorMode = false)
        val state = viewModel(route(length = QuizLength.MARKED_FOR_REVISION)).loaded()
        assertEquals(marked.take(2).toSet(), state.questions.map { it.question.key }.toSet())
    }
}

