package io.github.fianna76.studypack.testing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.fianna76.studypack.data.StudyRepository
import io.github.fianna76.studypack.data.db.StudyPackDatabase
import io.github.fianna76.studypack.data.files.NoteFileStore
import io.github.fianna76.studypack.data.pdf.PdfDocuments
import io.github.fianna76.studypack.importing.ImportCoordinator
import io.github.fianna76.studypack.importing.ImportState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.Closeable
import java.io.File

/** The data layer on an in-memory database and temporary folders, as the app wires it. */
class TestGraph(root: File, var now: Long = 1_000L) : Closeable {
    val context: Context = ApplicationProvider.getApplicationContext()
    val database = StudyPackDatabase.inMemory(context)
    val files = NoteFileStore(File(root, "files").apply { mkdirs() }, File(root, "cache").apply { mkdirs() })
    val pdfs = PdfDocuments(context)
    val repository = StudyRepository(database, files, clock = { now })
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val importer by lazy { ImportCoordinator(context.contentResolver, context.assets, repository, files, pdfs, scope) }

    /** Waits for the import queue to finish and returns its reports, clearing them. */
    fun awaitReports(): ImportState = runBlocking {
        val state = withTimeout(TIMEOUT_MS) { importer.state.first { !it.running && it.decision == null && it.reports.isNotEmpty() } }
        importer.dismissReports()
        state
    }

    fun awaitDecision(): ImportState = runBlocking {
        withTimeout(TIMEOUT_MS) { importer.state.first { it.decision != null } }
    }

    override fun close() {
        scope.cancel()
        database.close()
    }

    companion object {
        const val TIMEOUT_MS = 20_000L
    }
}

/** Reads a test resource as bytes. */
fun resource(name: String): ByteArray =
    checkNotNull(TestGraph::class.java.classLoader!!.getResourceAsStream(name)) { "Missing resource $name" }.use { it.readBytes() }
