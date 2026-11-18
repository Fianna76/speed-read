package io.github.fianna76.studypack.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.fianna76.studypack.StudyPackApplication
import io.github.fianna76.studypack.data.ModuleSummary
import io.github.fianna76.studypack.data.StudyRepository
import io.github.fianna76.studypack.importing.ImportCoordinator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class LibraryUiState(
    val isLoading: Boolean = true,
    val modules: List<ModuleSummary> = emptyList(),
)

class LibraryViewModel(
    repository: StudyRepository,
    private val importer: ImportCoordinator,
) : ViewModel() {

    val uiState: StateFlow<LibraryUiState> = repository.observeModules()
        .map { LibraryUiState(isLoading = false, modules = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun import(uris: List<Uri>) = importer.enqueue(uris)

    fun loadSample() = importer.importSample()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as StudyPackApplication).container
                LibraryViewModel(container.repository, container.importCoordinator)
            }
        }
    }
}
