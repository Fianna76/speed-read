package io.github.fianna76.speedread.data.news

import android.util.Patterns
import io.github.fianna76.speedread.news.FeedItem
import io.github.fianna76.speedread.news.NewsSources
import io.github.fianna76.speedread.rsvp.Tokenizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URI

/**
 * Speed-reads what is shared to the app (Share → SpeedRead) or copied to the clipboard. A web
 * link is downloaded and its article extracted; other text is read as it is.
 */
class SharedLinkReader(private val articles: ArticleRepository, private val scope: CoroutineScope) {

    sealed interface State {
        data object Idle : State

        /** Downloading [url]; `null` while saving plain text. */
        data class Preparing(val url: String?) : State

        /** The article is ready; the UI opens the reader and calls [onOpened]. */
        data class Ready(val bookId: Long) : State

        data class Failed(val url: String, val error: ArticleError) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null

    /**
     * Starts reading the first web link in [text] or, if there is none, the text itself.
     * Returns `false` if there is nothing worth reading (blank, or a word or two).
     */
    fun read(text: String, subject: String? = null): Boolean {
        if (findUrl(text) != null) return open(text, subject)
        if (Tokenizer.countWords(listOf(text)) < MIN_TEXT_WORDS) return false
        job?.cancel()
        _state.value = State.Preparing(null)
        job = scope.launch {
            _state.value = when (val result = articles.saveText(text, subject?.trim()?.takeUnless { it.startsWith("http") })) {
                is ArticleResult.Ready -> State.Ready(result.bookId)
                // Plain text can only fail to save, never to download; there is nothing to open online.
                is ArticleResult.Failed -> State.Failed("", result.reason)
            }
        }
        return true
    }

    /** Starts reading the first web link in [text]; returns `false` if there is none. */
    fun open(text: String, subject: String? = null): Boolean {
        val url = findUrl(text) ?: return false
        job?.cancel()
        _state.value = State.Preparing(url)
        job = scope.launch {
            val host = runCatching { URI(url).host?.removePrefix("www.") }.getOrNull().orEmpty()
            val source = NewsSources.ALL.firstOrNull { host.isNotEmpty() && host == URI(it.homepage).host?.removePrefix("www.") }
            // A subject is usually the page title; a bare link has none and the page title is used.
            val title = subject?.trim()?.takeUnless { it.isEmpty() || it.startsWith("http") }.orEmpty()
            val item = FeedItem(source?.id ?: WEB_SOURCE, title, url, summary = "", imageUrl = null, publishedAt = null, author = null)
            _state.value = when (val result = articles.open(item)) {
                is ArticleResult.Ready -> State.Ready(result.bookId)
                is ArticleResult.Failed -> State.Failed(url, result.reason)
            }
        }
        return true
    }

    fun cancel() {
        job?.cancel()
        _state.value = State.Idle
    }

    fun onOpened() = cancel()

    companion object {
        /** Source id of articles that came from a shared link rather than a feed. */
        const val WEB_SOURCE = "web"

        /** Shorter text is more likely a stray copy (a name, a number) than something to read. */
        const val MIN_TEXT_WORDS = 3

        internal fun findUrl(text: String): String? {
            val matcher = Patterns.WEB_URL.matcher(text)
            while (matcher.find()) {
                val candidate = matcher.group()
                if (candidate.startsWith("https://", ignoreCase = true) || candidate.startsWith("http://", ignoreCase = true)) {
                    return candidate
                }
            }
            return null
        }
    }
}
