package io.github.fianna76.studypack.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.fianna76.studypack.data.StudyRepository
import io.github.fianna76.studypack.data.db.StudyPackDatabase
import io.github.fianna76.studypack.data.files.NoteFileStore
import io.github.fianna76.studypack.data.pdf.PdfDocuments
import io.github.fianna76.studypack.data.settings.ReadingPreferences
import io.github.fianna76.studypack.importing.ImportCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency injection: the one place that creates and owns app-wide singletons. At this
 * size a hand-written container keeps the build simple (Hilt would be overkill).
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Scope for work that must outlive any single screen (imports, saving results). */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database by lazy { StudyPackDatabase.create(appContext) }

    val files by lazy { NoteFileStore(appContext.filesDir, appContext.cacheDir) }

    val pdfDocuments by lazy { PdfDocuments(appContext) }

    val repository by lazy { StudyRepository(database, files) }

    /** The container is the single owner of the DataStore, as DataStore requires one instance per file. */
    val readingPreferences by lazy {
        ReadingPreferences(PreferenceDataStoreFactory.create { appContext.preferencesDataStoreFile("settings") })
    }

    val importCoordinator by lazy {
        ImportCoordinator(appContext.contentResolver, appContext.assets, repository, files, pdfDocuments, applicationScope)
    }
}
