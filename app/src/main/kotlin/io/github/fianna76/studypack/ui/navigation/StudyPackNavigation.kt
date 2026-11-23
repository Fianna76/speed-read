package io.github.fianna76.studypack.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.fianna76.studypack.ui.library.LibraryScreen
import io.github.fianna76.studypack.ui.module.ModuleScreen
import io.github.fianna76.studypack.ui.pdf.PdfScreen
import io.github.fianna76.studypack.ui.quiz.QuizScreen
import io.github.fianna76.studypack.ui.rsvp.RsvpScreen
import io.github.fianna76.studypack.ui.setup.SetupScreen
import kotlinx.serialization.Serializable

/** Modules with their packs and notes; the start destination. */
@Serializable
data object LibraryRoute : NavKey

/** One module: its notes PDFs and question packs. */
@Serializable
data class ModuleRoute(val moduleCode: String) : NavKey

/** Quiz setup for every pack in a module, or for one pack ([packId]). */
@Serializable
data class SetupRoute(val moduleCode: String, val packId: String? = null) : NavKey

/**
 * A quiz session. Everything needed to rebuild it is here (the [seed] makes it reproducible);
 * answers in progress live in the quiz's saved state.
 */
@Serializable
data class QuizRoute(
    val moduleCode: String,
    val packIds: List<String>,
    /** Topic filter; `null` means all topics. */
    val topicIds: List<String>? = null,
    val demoAskedOnly: Boolean = false,
    /** A [io.github.fianna76.studypack.quiz.QuizLength] name, ignored for retries. */
    val length: String,
    val examMode: Boolean,
    /** For "retry missed": the encoded keys of the questions to ask again. */
    val retryKeys: List<String> = emptyList(),
    val seed: Long,
) : NavKey

/** The notes viewer. */
@Serializable
data class PdfRoute(val noteId: Long) : NavKey

/**
 * Speed reading a notes PDF, from [startPage] (zero-based) or, when `null`, from where reading
 * last stopped (the first section, past the title page and contents, the first time).
 */
@Serializable
data class RsvpRoute(val noteId: Long, val startPage: Int? = null) : NavKey

/**
 * App navigation built on Navigation 3: the back stack is plain, saveable state owned here, and
 * every entry gets its own ViewModelStore, so screen ViewModels are cleared when popped.
 */
@Composable
fun StudyPackNavigation(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(LibraryRoute)
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<LibraryRoute> {
                LibraryScreen(onOpenModule = { backStack.navigate(ModuleRoute(it)) })
            }
            entry<ModuleRoute> { route ->
                ModuleScreen(
                    moduleCode = route.moduleCode,
                    onBack = { backStack.removeLastOrNull() },
                    onQuiz = { packId -> backStack.navigate(SetupRoute(route.moduleCode, packId)) },
                    onOpenNote = { backStack.navigate(PdfRoute(it)) },
                    onSpeedRead = { noteId, page -> backStack.navigate(RsvpRoute(noteId, page)) },
                )
            }
            entry<SetupRoute> { route ->
                SetupScreen(
                    moduleCode = route.moduleCode,
                    packId = route.packId,
                    onBack = { backStack.removeLastOrNull() },
                    onStart = { backStack.navigate(it) },
                )
            }
            entry<QuizRoute> { route ->
                QuizScreen(
                    route = route,
                    onExit = { backStack.removeLastOrNull() },
                    onRetry = { retry ->
                        // The retry replaces this session, so Back still returns to the setup screen.
                        backStack.removeLastOrNull()
                        backStack.add(retry)
                    },
                )
            }
            entry<PdfRoute> { route ->
                PdfScreen(
                    noteId = route.noteId,
                    onBack = { backStack.removeLastOrNull() },
                    onSpeedRead = { page -> backStack.navigate(RsvpRoute(route.noteId, page)) },
                )
            }
            entry<RsvpRoute> { route ->
                RsvpScreen(noteId = route.noteId, startPage = route.startPage, onBack = { backStack.removeLastOrNull() })
            }
        },
    )
}

/** Pushes [route] unless it is already on top (guards against double taps). */
private fun NavBackStack<NavKey>.navigate(route: NavKey) {
    if (lastOrNull() != route) add(route)
}
