package io.github.fianna76.studypack.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.fianna76.studypack.StudyPackApplication
import io.github.fianna76.studypack.data.QuizContent
import io.github.fianna76.studypack.data.StudyRepository
import io.github.fianna76.studypack.quiz.QuestionHistory
import io.github.fianna76.studypack.quiz.QuestionKey
import io.github.fianna76.studypack.quiz.QuizLength
import io.github.fianna76.studypack.quiz.SessionBuilder
import io.github.fianna76.studypack.ui.navigation.QuizRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

data class TopicChip(val id: String, val name: String, val count: Int)

/** A quiz length on the setup screen. [count] is how many questions it would ask, where that varies. */
data class LengthOption(val length: QuizLength, val enabled: Boolean, val count: Int? = null)

data class SetupUiState(
    val isLoading: Boolean = true,
    /** The pack's title when quizzing one pack, otherwise `null` (all packs in the module). */
    val packTitle: String? = null,
    val lengths: List<LengthOption> = emptyList(),
    val length: QuizLength = QuizLength.BENCH_DEMO,
    val topics: List<TopicChip> = emptyList(),
    val selectedTopics: Set<String> = emptySet(),
    val demoAskedAvailable: Boolean = false,
    val demoAskedOnly: Boolean = false,
    val examMode: Boolean = false,
    val weakSpotsAvailable: Boolean = false,
    /** Questions the session would have. */
    val sessionSize: Int = 0,
    /** Topics those questions come from. */
    val sessionTopics: Int = 0,
) {
    val allTopicsSelected: Boolean get() = topics.isNotEmpty() && selectedTopics.size == topics.size
    val canStart: Boolean get() = !isLoading && sessionSize > 0
}

private data class Choices(
    val length: QuizLength = QuizLength.BENCH_DEMO,
    /** `null` until content loads, then every topic. */
    val topics: Set<String>? = null,
    val demoAskedOnly: Boolean = false,
    val examMode: Boolean = false,
)

private data class Loaded(val packIds: List<String>, val packTitle: String?, val content: QuizContent)

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModel(
    private val moduleCode: String,
    private val packId: String?,
    private val repository: StudyRepository,
    private val randomSeed: () -> Long = { Random.nextLong() },
) : ViewModel() {

    private val loaded = MutableStateFlow<Loaded?>(null)
    private val choices = MutableStateFlow(Choices())

    private val history = loaded.flatMapLatest { content ->
        if (content == null) emptyFlow() else repository.observeHistory(content.packIds).onStart { emit(emptyMap()) }
    }

    val uiState: StateFlow<SetupUiState> = combine(loaded, choices, history.onStart { emit(emptyMap()) }) { loaded, choices, history ->
        if (loaded == null) SetupUiState() else state(loaded, choices, history)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SetupUiState())

    init {
        viewModelScope.launch {
            val packIds = packId?.let(::listOf) ?: repository.packIdsInModule(moduleCode)
            val content = repository.quizContent(packIds)
            val title = packId?.let { repository.pack(it)?.title }
            choices.update { it.copy(topics = content.topics.map { t -> t.id }.toSet()) }
            loaded.value = Loaded(packIds, title, content)
        }
    }

    private fun state(loaded: Loaded, choices: Choices, history: Map<QuestionKey, QuestionHistory>): SetupUiState {
        val questions = loaded.content.questions
        val selected = choices.topics ?: emptySet()
        val demoAvailable = questions.any { it.isDemoAsked }
        val demoOnly = choices.demoAskedOnly && demoAvailable
        val pool = SessionBuilder.filterPool(questions, selected, demoOnly)
        val weakAvailable = SessionBuilder.hasWeakSpots(pool, history)
        val markedAvailable = SessionBuilder.hasMarkedForRevision(pool, history)
        val size = SessionBuilder.sessionSize(pool, choices.length, history)
        val topicsInPool = pool.map { it.topicId }.toSet().size
        val sessionTopics = when (choices.length) {
            QuizLength.EVERYTHING -> topicsInPool
            QuizLength.WEAK_SPOTS -> pool.filter { (history[it.key]?.wrong ?: 0) > 0 }.map { it.topicId }.toSet().size
            QuizLength.MARKED_FOR_REVISION -> pool.filter { history[it.key]?.isMarkedForRevision == true }.map { it.topicId }.toSet().size
            else -> minOf(topicsInPool, size)
        }
        val chipPool = SessionBuilder.filterPool(questions, null, demoOnly)
        return SetupUiState(
            isLoading = false,
            packTitle = loaded.packTitle,
            lengths = QuizLength.entries.map { length ->
                when (length) {
                    QuizLength.WEAK_SPOTS ->
                        LengthOption(length, weakAvailable, SessionBuilder.sessionSize(pool, length, history).takeIf { weakAvailable })
                    QuizLength.MARKED_FOR_REVISION ->
                        LengthOption(length, markedAvailable, SessionBuilder.sessionSize(pool, length, history).takeIf { markedAvailable })
                    else -> LengthOption(length, enabled = true)
                }
            },
            length = choices.length,
            topics = loaded.content.topics.map { topic ->
                TopicChip(topic.id, topic.name, SessionBuilder.slotCount(chipPool.filter { it.topicId == topic.id }))
            },
            selectedTopics = selected,
            demoAskedAvailable = demoAvailable,
            demoAskedOnly = demoOnly,
            examMode = choices.examMode,
            weakSpotsAvailable = weakAvailable,
            sessionSize = size,
            sessionTopics = sessionTopics,
        )
    }

    fun selectLength(length: QuizLength) = choices.update { it.copy(length = length) }

    fun toggleTopic(id: String) = choices.update {
        val current = it.topics.orEmpty()
        it.copy(topics = if (id in current) current - id else current + id)
    }

    fun selectAllTopics() {
        val all = loaded.value?.content?.topics?.map { it.id }?.toSet() ?: return
        choices.update { it.copy(topics = all) }
    }

    fun setDemoAskedOnly(on: Boolean) = choices.update { it.copy(demoAskedOnly = on) }

    fun setExamMode(on: Boolean) = choices.update { it.copy(examMode = on) }

    /** The quiz to start with the current choices, or `null` if nothing would be asked. */
    fun quizRoute(): QuizRoute? {
        val state = uiState.value
        val loaded = loaded.value ?: return null
        if (!state.canStart) return null
        return QuizRoute(
            moduleCode = moduleCode,
            packIds = loaded.packIds,
            topicIds = if (state.allTopicsSelected) null else state.selectedTopics.toList(),
            demoAskedOnly = state.demoAskedOnly,
            length = state.length.name,
            examMode = state.examMode,
            seed = randomSeed(),
        )
    }

    companion object {
        fun factory(moduleCode: String, packId: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as StudyPackApplication).container
                SetupViewModel(moduleCode, packId, container.repository)
            }
        }
    }
}

