package io.github.fianna76.studypack.ui.pdf

import android.graphics.Bitmap
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.fianna76.studypack.StudyPackApplication
import io.github.fianna76.studypack.data.NoteFile
import io.github.fianna76.studypack.data.StudyRepository
import io.github.fianna76.studypack.data.files.NoteFileStore
import io.github.fianna76.studypack.data.pdf.PdfPageRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PdfUiState(
    val isLoading: Boolean = true,
    val failed: Boolean = false,
    val note: NoteFile? = null,
    val pageCount: Int = 0,
    val aspectRatios: List<Float> = emptyList(),
    /** Page to open at (the last page read). */
    val initialPage: Int = 0,
)

/**
 * The notes viewer: renders pages on demand at screen width (sharper while zoomed in), keeps
 * only nearby pages in memory, and remembers the last page per PDF.
 */
@OptIn(FlowPreview::class)
class PdfViewModel(
    private val noteId: Long,
    private val repository: StudyRepository,
    private val files: NoteFileStore,
    private val writeScope: CoroutineScope,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PdfUiState())
    val uiState: StateFlow<PdfUiState> = _uiState.asStateFlow()

    private var renderer: PdfPageRenderer? = null
    private val currentPage = MutableStateFlow(-1)

    /** Rendered pages, bounded by memory rather than count. */
    private val cache = object : LruCache<Pair<Int, Int>, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: Pair<Int, Int>, value: Bitmap) = value.byteCount
    }

    init {
        viewModelScope.launch {
            val note = repository.note(noteId)
            val opened = note?.let { runCatching { withContext(Dispatchers.IO) { PdfPageRenderer.open(files.file(it.internalPath)) } }.getOrNull() }
            renderer = opened
            _uiState.update {
                PdfUiState(
                    isLoading = false,
                    failed = opened == null,
                    note = note,
                    pageCount = opened?.pageCount ?: 0,
                    aspectRatios = opened?.aspectRatios.orEmpty(),
                    initialPage = note?.lastPage?.coerceIn(0, ((opened?.pageCount ?: 1) - 1).coerceAtLeast(0)) ?: 0,
                )
            }
        }
        viewModelScope.launch {
            currentPage.drop(1).distinctUntilChanged().debounce(SAVE_DELAY_MS).collect { page ->
                if (page >= 0) repository.updateLastPage(noteId, page)
            }
        }
    }

    /** The page [index] rendered [widthPx] wide, from the cache or freshly rendered. */
    suspend fun page(index: Int, widthPx: Int): Bitmap? {
        val key = index to widthPx
        cache.get(key)?.let { return it }
        val bitmap = renderer?.render(index, widthPx) ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    fun onPageVisible(index: Int) {
        currentPage.value = index
    }

    /** The visible page, for "speed-read from this page". */
    val visiblePage: Int get() = currentPage.value.takeIf { it >= 0 } ?: _uiState.value.initialPage

    fun file(): File? = _uiState.value.note?.let { files.file(it.internalPath) }

    override fun onCleared() {
        val page = currentPage.value
        if (page >= 0) writeScope.launch { repository.updateLastPage(noteId, page) }
        renderer?.close()
        renderer = null
        cache.evictAll()
    }

    companion object {
        private const val SAVE_DELAY_MS = 600L
        private val CACHE_BYTES = (Runtime.getRuntime().maxMemory() / 6).coerceAtMost(96L * 1024 * 1024).toInt()

        fun factory(noteId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as StudyPackApplication).container
                PdfViewModel(noteId, container.repository, container.files, container.applicationScope)
            }
        }
    }
}
