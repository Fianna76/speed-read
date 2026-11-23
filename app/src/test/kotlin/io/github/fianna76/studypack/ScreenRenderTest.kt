package io.github.fianna76.studypack

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.speedread.rsvp.PlaybackSettings
import io.github.fianna76.speedread.rsvp.PlayerState
import io.github.fianna76.speedread.rsvp.Tokenizer
import io.github.fianna76.studypack.data.pdf.SectionKind
import io.github.fianna76.studypack.data.ModuleSummary
import io.github.fianna76.studypack.data.NoteFile
import io.github.fianna76.studypack.data.PackSummary
import io.github.fianna76.studypack.quiz.PackParser
import io.github.fianna76.studypack.quiz.QuizLength
import io.github.fianna76.studypack.quiz.Scoring
import io.github.fianna76.studypack.quiz.SessionBuilder
import io.github.fianna76.studypack.quiz.StudyPack
import io.github.fianna76.studypack.testing.PHONE
import io.github.fianna76.studypack.ui.library.LibraryContent
import io.github.fianna76.studypack.ui.library.LibraryUiState
import io.github.fianna76.studypack.ui.module.ModuleActions
import io.github.fianna76.studypack.ui.module.ModuleContent
import io.github.fianna76.studypack.ui.module.ModuleUiState
import io.github.fianna76.studypack.ui.quiz.QuizActions
import io.github.fianna76.studypack.ui.quiz.QuizContent
import io.github.fianna76.studypack.ui.quiz.QuizUiState
import io.github.fianna76.studypack.ui.rsvp.ChapterInfo
import io.github.fianna76.studypack.ui.rsvp.RsvpActions
import io.github.fianna76.studypack.ui.rsvp.RsvpContent
import io.github.fianna76.studypack.ui.rsvp.RsvpUiState
import io.github.fianna76.studypack.ui.setup.LengthOption
import io.github.fianna76.studypack.ui.setup.SetupActions
import io.github.fianna76.studypack.ui.setup.SetupContent
import io.github.fianna76.studypack.ui.setup.SetupUiState
import io.github.fianna76.studypack.ui.setup.TopicChip
import io.github.fianna76.studypack.ui.theme.StudyPackTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

/**
 * Renders every screen in light and dark themes on a phone-sized display with real graphics.
 * A smoke test (each screen composes, lays out and draws) that also writes PNGs to
 * `app/build/outputs/screenshots/` for visual review and the README.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE)
class ScreenRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val sample: StudyPack by lazy {
        val bytes = context.assets.open("sample/demo.studypack.json").use { it.readBytes() }
        checkNotNull(PackParser.parse(bytes).pack)
    }

    private val topicNames by lazy { sample.topics.associate { it.id to it.name } }

    private val modules = listOf(
        ModuleSummary("DEMO101", "Sample module: circuit basics", packCount = 1, noteCount = 1, questionCount = 19, lastPercent = 81),
        ModuleSummary("EE5105", "Applied Circuit Design for Medical Electronics", packCount = 2, noteCount = 1, questionCount = 183, lastPercent = 88),
        ModuleSummary("EE5110", "Biomedical Signal Processing", packCount = 1, noteCount = 0, questionCount = 64, lastPercent = null),
    )

    private fun session(examMode: Boolean = false) = SessionBuilder.build(sample.questions, QuizLength.BENCH_DEMO, emptyMap(), Random(3))
        .let { list -> QuizUiState(isLoading = false, examMode = examMode, questions = list, answers = List(list.size) { null }, topicNames = topicNames) }

    @Test
    fun library() = capture("library") {
        LibraryContent(LibraryUiState(isLoading = false, modules = modules), onOpenModule = {}, onImport = {}, onLoadSample = {})
    }

    @Test
    fun libraryEmpty() = capture("library_empty") {
        LibraryContent(LibraryUiState(isLoading = false), onOpenModule = {}, onImport = {}, onLoadSample = {})
    }

    @Test
    fun module() = capture("module") {
        ModuleContent(
            ModuleUiState(
                isLoading = false,
                code = "DEMO101",
                name = "Sample module: circuit basics",
                notes = listOf(NoteFile(1, "DEMO101", "DEMO101-circuits-overview.pdf", "Circuit Basics: Overview", "notes/x.pdf", 5, 2)),
                packs = listOf(
                    PackSummary("demo101-circuit-basics", "Circuit Basics (sample pack)", "1.0.0", null, 19, bestPercent = 94, lastPercent = 81),
                ),
            ),
            ModuleActions(),
        )
    }

    @Test
    fun setup() = capture("setup") {
        SetupContent(
            SetupUiState(
                isLoading = false,
                lengths = QuizLength.entries.map { LengthOption(it, enabled = true) },
                length = QuizLength.BENCH_DEMO,
                topics = sample.topics.map { t -> TopicChip(t.id, t.name, SessionBuilder.slotCount(sample.questions.filter { it.topicId == t.id })) },
                selectedTopics = sample.topics.map { it.id }.toSet(),
                demoAskedAvailable = true,
                weakSpotsAvailable = true,
                sessionSize = 16,
                sessionTopics = 4,
            ),
            subtitle = "DEMO101",
            actions = SetupActions(),
        )
    }

    @Test
    fun quizTutorRevealed() = capture("quiz_tutor") {
        val state = session()
        val first = state.questions.indexOfFirst { it.question.sayIt != null }
        val question = state.questions[first]
        QuizContent(
            state.copy(
                index = first,
                answers = state.answers.toMutableList().also {
                    for (i in 0 until first) it[i] = state.questions[i].correctIndex
                    it[first] = (question.correctIndex + 1) % 4
                },
            ),
            QuizActions(),
        )
    }

    @Test
    fun quizLatex() = capture("quiz_latex") {
        // A calculation variant with formulas in the prompt and a display formula in the explanation.
        val state = session()
        val index = state.questions.indexOfFirst { it.question.variantGroup != null }
        val question = state.questions[index]
        QuizContent(
            state.copy(index = index, answers = state.answers.toMutableList().also { it[index] = question.correctIndex }),
            QuizActions(),
        )
    }

    @Test
    fun quizLatexOptions() = capture("quiz_latex_options") {
        val q = sample.questions.first { it.id == "rc-003" }.copy(
            correct = "\$f_c = \\frac{1}{2\\pi RC} \\approx 1\\,\\mathrm{kHz}\$",
            distractors = listOf("\$\\frac{1}{RC} \\approx 6.3\\,\\mathrm{kHz}\$", "\$2\\pi RC \\approx 1\\,\\mathrm{ms}\$", "\$\\sqrt{RC}\$ (not a frequency)"),
        )
        val asked = SessionBuilder.shuffleOptions(q, Random(1))
        QuizContent(
            QuizUiState(isLoading = false, questions = listOf(asked), answers = listOf(null), topicNames = topicNames),
            QuizActions(),
        )
    }

    @Test
    fun latexSymbols() = capture("latex_symbols") {
        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)) {
            io.github.fianna76.studypack.ui.components.MathText(
                "Units: \$10\\,\\mathrm{k}\\Omega\$, \$\\Omega_0\$, \$4.7\\,\\mu\\mathrm{F}\$, \$\\tau = RC\$, " +
                    "\$\\Delta V\$, \$a \\otimes b\$, \$\\pm 5\\,\\%\$, \$\\leq\$ and \$\\approx\$. " +
                    "\$\$\\mathrm{CMRR} = 20\\log_{10}\\left|\\frac{A_d}{A_c}\\right|\\,\\mathrm{dB}\$\$ Broken: \$\\frac{1}{\$ stays readable.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            )
        }
    }

    @Test
    fun quizExam() = capture("quiz_exam") {
        val state = session(examMode = true)
        QuizContent(state.copy(index = 3, answers = state.answers.mapIndexed { i, a -> if (i < 3) 0 else a }, selection = 1), QuizActions())
    }

    @Test
    fun results() = capture("results") {
        val state = session()
        val answers = state.questions.mapIndexed { i, q -> if (i % 5 == 2) (q.correctIndex + 1) % 4 else q.correctIndex }
        QuizContent(
            state.copy(finished = true, answers = answers, score = Scoring.score(state.questions, answers, topicNames)),
            QuizActions(),
        )
    }

    private val readerChapters = listOf(
        ChapterInfo(null, SectionKind.FRONT, 40, 0, 1),
        ChapterInfo("1. Ohm's law and power", SectionKind.BODY, 120, 2, 2),
        ChapterInfo("2. Series and parallel", SectionKind.BODY, 130, 3, 3),
        ChapterInfo("3. RC circuits", SectionKind.BODY, 110, 4, 4),
        ChapterInfo("4. Op-amps", SectionKind.BODY, 140, 5, 5),
        ChapterInfo("5. Formula sheet", SectionKind.BODY, 50, 6, 6),
        ChapterInfo("Sources", SectionKind.BACK, 15, 6, 6),
    )

    private val readerText = Tokenizer.tokenize(
        listOf(
            "3. RC circuits",
            "The time constant of a resistor and capacitor is τ = RC. A charging capacitor reaches about 63 % of the supply " +
                "after one time constant and more than 99 % after five.",
            "An RC low-pass filter has its −3 dB corner at fc = 1 / (2πRC). At the corner the output phase lags the input by " +
                "45 degrees, heading towards 90 degrees well above it.",
        ),
    )

    private val readerUi = RsvpUiState(isLoading = false, title = "Circuit Basics: Overview", chapters = readerChapters, chapterIndex = 3, showTapHint = false)

    private val readerPlayer = PlayerState(readerText, index = 12, isPlaying = false, settings = PlaybackSettings(wordsPerMinute = 350))

    @Test
    fun speedReading() = capture("rsvp") {
        RsvpContent(readerUi, readerPlayer, RsvpActions())
    }

    @Test
    fun speedReadingPageView() = capture("rsvp_page_view") {
        RsvpContent(readerUi, readerPlayer, RsvpActions(), initialPageView = true)
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        var dark by mutableStateOf(false)
        compose.setContent { StudyPackTheme(darkTheme = dark, dynamicColor = false, content = content) }
        save(name)
        dark = true
        compose.waitForIdle()
        save("${name}_dark")
    }

    private fun save(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = File("build/outputs/screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
