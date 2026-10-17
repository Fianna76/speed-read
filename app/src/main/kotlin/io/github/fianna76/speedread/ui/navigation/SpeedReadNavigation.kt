package io.github.fianna76.speedread.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.fianna76.speedread.ui.library.LibraryScreen
import io.github.fianna76.speedread.ui.news.BriefingRequest
import io.github.fianna76.speedread.ui.news.NewsstandScreen
import io.github.fianna76.speedread.ui.reader.ReaderScreen
import io.github.fianna76.speedread.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

/** Library of imported books; the start destination. */
@Serializable
data object LibraryRoute : NavKey

/** Headlines from news outlets, to read online or with RSVP. */
@Serializable
data object NewsstandRoute : NavKey

/** RSVP reader for one book (or saved news article). */
@Serializable
data class ReaderRoute(val bookId: Long, val autoPlay: Boolean = false) : NavKey

@Serializable
data object SettingsRoute : NavKey

/** A destination requested from outside the navigation (widget taps, notifications). */
sealed interface NavigationRequest {
    /**
     * The Newsstand, optionally previewing the story at [previewUrl] (canonical URL), or
     * showing or playing the [briefing].
     */
    data class OpenNews(val previewUrl: String? = null, val briefing: BriefingRequest? = null) : NavigationRequest
}

/**
 * App navigation built on Navigation 3: the back stack is plain, saveable state owned here,
 * and every entry gets its own ViewModelStore so screen ViewModels are cleared when popped.
 */
@Composable
fun SpeedReadNavigation(
    modifier: Modifier = Modifier,
    request: NavigationRequest? = null,
    onRequestHandled: () -> Unit = {},
) {
    val backStack = rememberNavBackStack(LibraryRoute)
    // A story to preview once the Newsstand shows (from a widget tap).
    var newsPreview by rememberSaveable { mutableStateOf<String?>(null) }
    var newsBriefing by rememberSaveable { mutableStateOf<BriefingRequest?>(null) }
    LaunchedEffect(request) {
        when (request) {
            is NavigationRequest.OpenNews -> {
                backStack.navigateTopLevel(TopLevelDestination.NEWS)
                newsPreview = request.previewUrl
                newsBriefing = request.briefing
            }
            null -> return@LaunchedEffect
        }
        onRequestHandled()
    }
    SharedLinkHost(onOpenBook = { bookId -> backStack.navigate(ReaderRoute(bookId)) })
    // Tapping News while on the Newsstand scrolls it back to the top.
    var newsReselected by rememberSaveable { mutableIntStateOf(0) }
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
                LibraryScreen(
                    onOpenBook = { bookId -> backStack.navigate(ReaderRoute(bookId)) },
                    onOpenSettings = { backStack.navigate(SettingsRoute) },
                    bottomBar = { TopLevelNavigationBar(TopLevelDestination.LIBRARY, backStack::navigateTopLevel) },
                )
            }
            entry<NewsstandRoute> {
                NewsstandScreen(
                    onOpenBook = { bookId, autoPlay -> backStack.navigate(ReaderRoute(bookId, autoPlay)) },
                    onOpenSettings = { backStack.navigate(SettingsRoute) },
                    bottomBar = {
                        TopLevelNavigationBar(TopLevelDestination.NEWS) { destination ->
                            if (destination == TopLevelDestination.NEWS) newsReselected++ else backStack.navigateTopLevel(destination)
                        }
                    },
                    scrollToTop = newsReselected,
                    previewRequest = newsPreview,
                    onPreviewRequestHandled = { newsPreview = null },
                    briefingRequest = newsBriefing,
                    onBriefingRequestHandled = { newsBriefing = null },
                )
            }
            entry<ReaderRoute> { route ->
                ReaderScreen(
                    bookId = route.bookId,
                    autoPlay = route.autoPlay,
                    onBack = { backStack.removeLastOrNull() },
                    onOpenNext = { next ->
                        // The next story replaces this one, so Back still returns to the Newsstand.
                        backStack.removeLastOrNull()
                        backStack.add(ReaderRoute(next, autoPlay = true))
                    },
                )
            }
            entry<SettingsRoute> {
                SettingsScreen(onBack = { backStack.removeLastOrNull() })
            }
        },
    )
}

/**
 * Switches section. The library always stays at the bottom of the stack, so Back from the
 * Newsstand returns to it (and keeps its state) before leaving the app.
 */
private fun NavBackStack<NavKey>.navigateTopLevel(destination: TopLevelDestination) {
    while (size > 1) removeAt(lastIndex)
    when (destination) {
        TopLevelDestination.LIBRARY -> Unit
        TopLevelDestination.NEWS -> add(NewsstandRoute)
    }
}

/** Pushes [route] unless it is already on top (guards against double taps). */
private fun NavBackStack<NavKey>.navigate(route: NavKey) {
    if (lastOrNull() != route) add(route)
}
