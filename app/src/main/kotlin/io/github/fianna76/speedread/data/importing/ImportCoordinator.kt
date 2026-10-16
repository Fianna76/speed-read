package io.github.fianna76.speedread.data.importing

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Application-scoped import queue. Imports run one at a time in [scope], so they finish even if
 * the screen that started them goes away, and requests from the file picker, "Open with" and
 * "Share" all funnel through the same path.
 */
class ImportCoordinator(
    private val importer: suspend (Uri) -> ImportResult,
    private val scope: CoroutineScope,
    private val beforeFirstImport: suspend () -> Unit = {},
) {
    private val mutex = Mutex()
    private var housekeepingDone = false // Guarded by mutex.
    private val _pendingCount = MutableStateFlow(0)
    private val _results = Channel<ImportResult>(Channel.UNLIMITED)

    /** Number of files queued or being imported. */
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    /** Results in completion order; buffered until collected, and delivered to one collector. */
    val results: Flow<ImportResult> = _results.receiveAsFlow()

    fun enqueue(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _pendingCount.update { it + uris.size }
        scope.launch {
            mutex.withLock {
                if (!housekeepingDone) {
                    housekeepingDone = true
                    beforeFirstImport()
                }
                for (uri in uris) {
                    try {
                        val result = try {
                            importer(uri)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // Never let one bad file take down the app-wide scope.
                            ImportResult.Failed(fileName = null, reason = ImportError.UNREADABLE)
                        }
                        _results.send(result)
                    } finally {
                        _pendingCount.update { it - 1 }
                    }
                }
            }
        }
    }
}
