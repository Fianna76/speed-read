package io.github.fianna76.studypack.ui.module

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.fianna76.studypack.StudyPackApplication
import io.github.fianna76.studypack.data.NoteFile
import io.github.fianna76.studypack.data.PackSummary
import io.github.fianna76.studypack.data.StudyRepository
import io.github.fianna76.studypack.importing.ImportCoordinator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ModuleUiState(
    val isLoading: Boolean = true,
    val code: String = "",
    val name: String? = null,
    val notes: List<NoteFile> = emptyList(),
    val packs: List<PackSummary> = emptyList(),
    /** The module no longer exists (everything in it was deleted). */
    val isGone: Boolean = false,
)

class ModuleViewModel(
    private val moduleCode: String,
    private val repository: StudyRepository,
    private val importer: ImportCoordinator,
) : ViewModel() {

    val uiState: StateFlow<ModuleUiState> = combine(
        repository.observeModule(moduleCode),
        repository.observeNotes(moduleCode),
        repository.observePacks(moduleCode),
    ) { module, notes, packs ->
        ModuleUiState(
            isLoading = false,
            code = moduleCode,
            name = module?.name,
            notes = notes,
            packs = packs,
            isGone = module == null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModuleUiState(code = moduleCode))

    fun deletePack(packId: String) {
        viewModelScope.launch { repository.deletePack(packId) }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch { repository.deleteNote(noteId) }
    }

    /** Deletes the module and everything in it; the screen closes once it is gone. */
    fun deleteModule() {
        viewModelScope.launch { repository.deleteModule(moduleCode) }
    }

    /** Imports files; notes that match no module land in this one. */
    fun importHere(uris: List<Uri>) = importer.enqueue(uris, defaultModule = moduleCode)

    companion object {
        fun factory(moduleCode: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as StudyPackApplication).container
                ModuleViewModel(moduleCode, container.repository, container.importCoordinator)
            }
        }
    }
}
