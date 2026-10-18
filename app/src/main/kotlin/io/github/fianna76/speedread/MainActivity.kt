package io.github.fianna76.speedread

import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fianna76.speedread.data.settings.ThemeMode
import io.github.fianna76.speedread.ui.navigation.NavigationRequest
import io.github.fianna76.speedread.ui.news.BriefingRequest
import io.github.fianna76.speedread.ui.navigation.SpeedReadNavigation
import io.github.fianna76.speedread.ui.theme.SpeedReadTheme
import io.github.fianna76.speedread.ui.theme.isDark
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Hold the splash until the theme preference is known, to avoid a light/dark flash.
        splashScreen.setKeepOnScreenCondition { viewModel.themeMode.value == null }

        if (savedInstanceState == null) handle(intent)

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val darkTheme = (themeMode ?: ThemeMode.SYSTEM).isDark()

            // Keep system bar icons legible when the app theme differs from the system theme.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
                )
                onDispose {}
            }

            SpeedReadTheme(darkTheme = darkTheme) {
                SpeedReadNavigation(request = navigationRequest, onRequestHandled = { navigationRequest = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Where an incoming intent (widget tap, shared link) wants the app to go. */
    private var navigationRequest by mutableStateOf<NavigationRequest?>(null)

    private fun handle(intent: Intent?) {
        when (intent?.action) {
            ACTION_OPEN_NEWS -> navigationRequest = NavigationRequest.OpenNews(previewUrl = intent.dataString)
            ACTION_OPEN_BRIEFING -> navigationRequest = NavigationRequest.OpenNews(briefing = BriefingRequest.SHOW)
            ACTION_PLAY_BRIEFING -> navigationRequest = NavigationRequest.OpenNews(briefing = BriefingRequest.PLAY)
            ACTION_CONTINUE_READING -> continueReading()
            // Android only lets the focused app read the clipboard: wait for window focus.
            ACTION_READ_CLIPBOARD -> clipboardPending = true
            Intent.ACTION_SEND if intent.type == "text/plain" -> readSharedLink(intent)
            else -> importFrom(intent)
        }
    }

    /** "Share → SpeedRead" with a web link (speed-read the article) or plain text (read it as it is). */
    private fun readSharedLink(intent: Intent) {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        val reader = (application as SpeedReadApplication).container.sharedLinkReader
        if (!reader.read(text, subject)) Toast.makeText(this, R.string.shared_link_none, Toast.LENGTH_LONG).show()
    }

    private var clipboardPending = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && clipboardPending) {
            clipboardPending = false
            readClipboard()
        }
    }

    /** Speed-reads the clipboard: a copied link's article, or copied text as it is. */
    private fun readClipboard() {
        val clip = getSystemService<ClipboardManager>()?.primaryClip
        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        val reader = (application as SpeedReadApplication).container.sharedLinkReader
        if (!reader.read(text)) Toast.makeText(this, R.string.clipboard_empty, Toast.LENGTH_LONG).show()
    }

    /** Opens the book or article read last, if it isn't finished; otherwise the library. */
    private fun continueReading() {
        lifecycleScope.launch {
            val bookId = (application as SpeedReadApplication).container.bookRepository.lastUnfinishedBookId()
            if (bookId != null) {
                navigationRequest = NavigationRequest.OpenBook(bookId)
            } else {
                Toast.makeText(this@MainActivity, R.string.shortcut_nothing_to_continue, Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Handles "Open with" (VIEW) and "Share" (SEND / SEND_MULTIPLE) intents carrying EPUB files. */
    private fun importFrom(intent: Intent?) {
        val uris: List<Uri> = when (intent?.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) {
            (application as SpeedReadApplication).container.importCoordinator.enqueue(uris)
        }
    }

    companion object {
        /** Opens the Newsstand; the intent's data, if any, is a story to preview. */
        const val ACTION_OPEN_NEWS = "io.github.fianna76.speedread.action.OPEN_NEWS"

        /** Opens the Newsstand with the briefing's headlines shown. */
        const val ACTION_OPEN_BRIEFING = "io.github.fianna76.speedread.action.OPEN_BRIEFING"

        /** Starts reading the briefing. */
        const val ACTION_PLAY_BRIEFING = "io.github.fianna76.speedread.action.PLAY_BRIEFING"

        /** Reopens the book or article read last. */
        const val ACTION_CONTINUE_READING = "io.github.fianna76.speedread.action.CONTINUE_READING"

        /** Speed-reads the clipboard (a link's article, or the copied text). */
        const val ACTION_READ_CLIPBOARD = "io.github.fianna76.speedread.action.READ_CLIPBOARD"

        // Same scrims enableEdgeToEdge() uses for 3-button navigation by default.
        private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
