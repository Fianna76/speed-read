package io.github.fianna76.studypack.ui.quiz

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.fianna76.studypack.StudyPackApplication
import io.github.fianna76.studypack.data.StudyRepository
import io.github.fianna76.studypack.quiz.Answers
import io.github.fianna76.studypack.quiz.QuestionKey
import io.github.fianna76.studypack.quiz.QuizLength
import io.github.fianna76.studypack.quiz.QuizScore
import io.github.fianna76.studypack.quiz.Scoring
import io.github.fianna76.studypack.quiz.SessionBuilder
import io.github.fianna76.studypack.quiz.SessionQuestion
import io.github.fianna76.studypack.ui.navigation.QuizRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

data class QuizUiState(
    val isLoading: Boolean = true,
    val failed: Boolean = false,
    val examMode: Boolean = false,
    val isRetry: Boolean = false,
    val questions: List<SessionQuestion> = emptyList(),
    /** Submitted answer (display index) per question, [Answers.SKIPPED] for "Don't know"; `null` while unanswered. */
    val answers: List<Int?> = emptyList(),
    val index: Int = 0,
    /** Exam mode: the option picked on the current question, changeable until Next. */
    val selection: Int? = null,
    val finished: Boolean = false,
    val topicNames: Map<String, String> = emptyMap(),
    val score: QuizScore? = null,
) {
    val current: SessionQuestion? get() = questions.getOrNull(index)

    val currentAnswer: Int? get() = answers.getOrNull(index)

    /** Tutor mode shows the answer as soon as the question is answered (or skipped). */
    val isRevealed: Boolean get() = !examMode && currentAnswer != null

    /** "Don't know" is offered until the question is answered. */
    val canSkip: Boolean get() = !finished && current != null && currentAnswer == null

    val canGoNext: Boolean get() = if (examMode) selection != null else currentAnswer != null

    val isLast: Boolean get() = index == questions.lastIndex

    val answeredCount: Int get() = answers.count { it != null }

    val skippedCount: Int get() = answers.count { it == Answers.SKIPPED }

    val correctCount: Int get() = questions.indices.count { answers[it] != null && answers[it] == questions[it].correctIndex }

    /** Indices of questions answered wrong (not skipped). */
    val missed: List<Int> get() = questions.indices.filter {
        answers[it] != null && answers[it] != Answers.SKIPPED && answers[it] != questions[it].correctIndex
    }

    /** Indices of questions skipped with "Don't know": marked for revision. */
    val skipped: List<Int> get() = questions.indices.filter { answers[it] == Answers.SKIPPED }

    /** Indices of questions answered right. */
    val right: List<Int> get() = questions.indices.filter { answers[it] != null && answers[it] == questions[it].correctIndex }
}

@Serializable
private data class SavedQuestion(val key: String, val order: List<Int>)

/** Everything needed to restore a session after rotation or process death. */
@Serializable
private data class SavedSession(
    val questions: List<SavedQuestion>,
    val answers: List<Int?>,
    val index: Int,
    val selection: Int? = null,
    val finished: Boolean = false,
    val startedAt: Long,
    val resultSaved: Boolean = false,
)

/**
 * Runs one quiz session: builds it from the route (seeded, so reproducible), records each answer
 * in the question history as it is given, and saves a summary when the session ends.
 *
 * Tutor mode locks an answer on tap and reveals the explanation; exam mode lets the answer change
 * until Next and reveals nothing until the results. Only answered questions reach the history.
 */
class QuizViewModel(
    private val route: QuizRoute,
    private val savedState: SavedStateHandle,
    private val repository: StudyRepository,
    /** Writes that must finish even if the screen is left straight away. */
    private val writeScope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuizUiState(examMode = route.examMode && route.retryKeys.isEmpty(), isRetry = route.retryKeys.isNotEmpty()))
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    private var startedAt = clock()
    private var resultSaved = false

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val content = repository.quizContent(route.packIds)
        val byKey = content.questions.associateBy { it.key }
        val saved = savedState.get<String>(KEY_SESSION)?.let { runCatching { json.decodeFromString(SavedSession.serializer(), it) }.getOrNull() }

        if (saved != null) {
            val questions = saved.questions.mapNotNull { q ->
                QuestionKey.decode(q.key)?.let(byKey::get)?.let { runCatching { SessionQuestion(it, q.order) }.getOrNull() }
            }
            if (questions.size != saved.questions.size) {
                _uiState.update { it.copy(isLoading = false, failed = true) }
                return
            }
            startedAt = saved.startedAt
            resultSaved = saved.resultSaved
            _uiState.update {
                it.copy(
                    isLoading = false,
                    questions = questions,
                    answers = saved.answers,
                    index = saved.index,
                    selection = saved.selection,
                    finished = saved.finished,
                    topicNames = content.topicNames,
                    score = if (saved.finished) Scoring.score(questions, saved.answers, content.topicNames) else null,
                )
            }
            return
        }

        val random = Random(route.seed)
        val questions = if (route.retryKeys.isNotEmpty()) {
            SessionBuilder.buildRetry(route.retryKeys.mapNotNull { QuestionKey.decode(it)?.let(byKey::get) }, random)
        } else {
            val pool = SessionBuilder.filterPool(content.questions, route.topicIds?.toSet(), route.demoAskedOnly)
            val length = runCatching { QuizLength.valueOf(route.length) }.getOrDefault(QuizLength.BENCH_DEMO)
            // Weak spots and revision quizzes are chosen from the answer history.
            val needsHistory = length == QuizLength.WEAK_SPOTS || length == QuizLength.MARKED_FOR_REVISION
            val history = if (needsHistory) repository.history(route.packIds) else emptyMap()
            SessionBuilder.build(pool, length, history, random)
        }
        val built = _uiState.value.copy(
            isLoading = false,
            failed = questions.isEmpty(),
            questions = questions,
            answers = List(questions.size) { null },
            topicNames = content.topicNames,
        )
        // Saved before it is shown, so a save can never overwrite an answer given meanwhile.
        save(built)
        _uiState.value = built
    }

    /** Taps an option: locks it in tutor mode, selects it in exam mode. */
    fun choose(option: Int) {
        val state = _uiState.value
        val question = state.current ?: return
        if (state.finished || option !in question.options.indices) return
        if (state.examMode) {
            _uiState.update { it.copy(selection = option) }
        } else {
            if (state.currentAnswer != null) return
            submit(option)
        }
        save()
    }

    fun next() {
        val state = _uiState.value
        if (state.finished || !state.canGoNext) return
        if (state.examMode) submit(state.selection ?: return)
        if (_uiState.value.isLast) {
            finish()
        } else {
            _uiState.update { it.copy(index = it.index + 1, selection = null) }
            save()
        }
    }

    /**
     * "Don't know": skips the current question and marks it for revision (not as wrong). Tutor
     * mode then shows the answer and explanation; exam mode moves straight on.
     */
    fun skip() {
        val state = _uiState.value
        val question = state.current ?: return
        if (!state.canSkip) return
        _uiState.update { it.copy(answers = it.answers.toMutableList().also { list -> list[it.index] = Answers.SKIPPED }, selection = null) }
        writeScope.launch { repository.recordSkip(question.question.key) }
        if (state.examMode) {
            if (state.isLast) {
                finish()
                return
            }
            _uiState.update { it.copy(index = it.index + 1) }
        }
        save()
    }

    /** Ends the session now. An exam-mode pick that was not confirmed with Next does not count. */
    fun endEarly() {
        if (_uiState.value.finished) return
        finish()
    }

    /** A new tutor-mode session over the questions answered wrong, or `null` if none were. */
    fun retryRoute(): QuizRoute? = retry(_uiState.value.missed)

    /** A new tutor-mode session over the questions skipped with "Don't know", or `null` if none were. */
    fun retrySkippedRoute(): QuizRoute? = retry(_uiState.value.skipped)

    private fun retry(indices: List<Int>): QuizRoute? {
        val state = _uiState.value
        val keys = indices.map { state.questions[it].question.key.encode() }
        if (keys.isEmpty()) return null
        return route.copy(retryKeys = keys, examMode = false, length = MODE_RETRY, seed = Random.nextLong())
    }

    private fun submit(option: Int) {
        val state = _uiState.value
        val question = state.current ?: return
        val correct = option == question.correctIndex
        _uiState.update { it.copy(answers = it.answers.toMutableList().also { list -> list[it.index] = option }, selection = null) }
        writeScope.launch { repository.recordAnswer(question.question.key, correct, tutorMode = !state.examMode) }
    }

    private fun finish() {
        _uiState.update { it.copy(finished = true, selection = null, score = Scoring.score(it.questions, it.answers, it.topicNames)) }
        val state = _uiState.value
        val score = state.score
        if (!resultSaved && score != null && score.answered > 0) {
            resultSaved = true
            val mode = if (state.isRetry) MODE_RETRY else route.length
            writeScope.launch {
                repository.saveSession(route.moduleCode, startedAt, mode, state.examMode, route.packIds, score, route.seed)
            }
        }
        save()
    }

    private fun save(state: QuizUiState = _uiState.value) {
        if (state.questions.isEmpty()) return
        val session = SavedSession(
            questions = state.questions.map { SavedQuestion(it.question.key.encode(), it.optionOrder) },
            answers = state.answers,
            index = state.index,
            selection = state.selection,
            finished = state.finished,
            startedAt = startedAt,
            resultSaved = resultSaved,
        )
        savedState[KEY_SESSION] = json.encodeToString(SavedSession.serializer(), session)
    }

    companion object {
        const val MODE_RETRY = "RETRY"
        private const val KEY_SESSION = "session"
        private val json = Json { ignoreUnknownKeys = true }

        fun factory(route: QuizRoute): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as StudyPackApplication).container
                QuizViewModel(route, createSavedStateHandle(), container.repository, container.applicationScope)
            }
        }
    }
}
