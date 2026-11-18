package io.github.fianna76.studypack.importing

import android.content.ContentResolver
import android.content.res.AssetManager
import android.net.Uri
import android.provider.OpenableColumns
import io.github.fianna76.studypack.data.StudyRepository
import io.github.fianna76.studypack.data.files.NoteFileStore
import io.github.fianna76.studypack.data.pdf.NoteTitles
import io.github.fianna76.studypack.data.pdf.PdfDocuments
import io.github.fianna76.studypack.data.pdf.PdfException
import io.github.fianna76.studypack.quiz.PackParser
import io.github.fianna76.studypack.quiz.PackVersion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * Imports question packs and notes PDFs, one batch at a time, from picked files, "Open with",
 * "Share" or the bundled sample.
 *
 * Each file is first copied to scratch storage and identified by its first bytes. Packs go
 * before PDFs, so a PDF named in a pack's `pack.notes` attaches to that pack's module even when
 * both are picked together. When the import needs an answer (replace an equal or newer version?
 * which module is this PDF for?) it publishes an [ImportDecision] in [state] and waits.
 */
class ImportCoordinator(
    private val resolver: ContentResolver,
    private val assets: AssetManager,
    private val repository: StudyRepository,
    private val files: NoteFileStore,
    private val pdfs: PdfDocuments,
    scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private sealed interface Source {
        data class Document(val uri: Uri) : Source
        data class Asset(val path: String) : Source
    }

    private class Batch(val sources: List<Source>, val defaultModule: String?)

    private class Staged(val fileName: String, val file: File?, val kind: FileKind, val error: String? = null)

    private val queue = Channel<Batch>(Channel.UNLIMITED)

    /** Batches queued or in progress. */
    private val pending = AtomicInteger(0)

    private val _state = MutableStateFlow(ImportState())
    val state: StateFlow<ImportState> = _state.asStateFlow()

    init {
        scope.launch {
            withContext(io) { files.clearTempFiles() }
            for (batch in queue) process(batch)
        }
    }

    /** Imports [uris]; a PDF that matches no module goes to [defaultModule] if given, otherwise the user is asked. */
    fun enqueue(uris: List<Uri>, defaultModule: String? = null) {
        if (uris.isEmpty()) return
        submit(Batch(uris.map { Source.Document(it) }, defaultModule))
    }

    /** Imports the bundled demo pack and its notes. */
    fun importSample() {
        submit(Batch(SAMPLE_FILES.map { Source.Asset(it) }, defaultModule = null))
    }

    private fun submit(batch: Batch) {
        pending.incrementAndGet()
        _state.update { it.copy(running = true) }
        queue.trySend(batch)
    }

    fun replace(decision: ImportDecision.ReplacePack, replace: Boolean) {
        decision.answer.complete(replace)
    }

    fun chooseModule(decision: ImportDecision.ChooseModule, choice: ModuleChoice?) {
        decision.answer.complete(choice)
    }

    fun dismissReports() {
        _state.update { it.copy(reports = emptyList()) }
    }

    private suspend fun process(batch: Batch) {
        _state.update { it.copy(running = true) }
        val staged = withContext(io) { batch.sources.map(::stage) }
        val ordered = staged.sortedBy {
            when (it.kind) {
                FileKind.PACK -> 0
                FileKind.PDF -> 1
                FileKind.UNKNOWN -> 2
            }
        }
        val reports = ordered.map { file ->
            val outcome = try {
                when {
                    file.error != null -> ImportOutcome.Failed(listOf(file.error))
                    file.kind == FileKind.PACK -> importPack(file)
                    file.kind == FileKind.PDF -> importPdf(file, batch.defaultModule)
                    else -> ImportOutcome.Failed(listOf("This is neither a question pack (.studypack.json) nor a PDF."))
                }
            } catch (e: IOException) {
                ImportOutcome.Failed(listOf("The file could not be imported: ${e.message ?: e.javaClass.simpleName}"))
            } finally {
                file.file?.let { withContext(io) { it.delete() } }
            }
            ImportReport(file.fileName, outcome)
        }
        val stillRunning = pending.decrementAndGet() > 0
        _state.update { it.copy(running = stillRunning, reports = it.reports + reports) }
    }

    /** Copies a source to scratch storage and identifies it. */
    private fun stage(source: Source): Staged {
        val name = when (source) {
            is Source.Document -> displayName(source.uri)
            is Source.Asset -> source.path.substringAfterLast('/')
        }
        return try {
            val input: InputStream = when (source) {
                is Source.Document -> resolver.openInputStream(source.uri) ?: throw IOException("The file could not be opened.")
                is Source.Asset -> assets.open(source.path)
            }
            val temp = files.newTempFile()
            val size = input.use { stream -> temp.outputStream().use { copyLimited(stream, it) } }
            if (size < 0) {
                temp.delete()
                return Staged(name, null, FileKind.UNKNOWN, "The file is larger than ${MAX_FILE_BYTES / 1024 / 1024} MB.")
            }
            val head = temp.inputStream().use { readHead(it) }
            val mime = (source as? Source.Document)?.uri?.let { runCatching { resolver.getType(it) }.getOrNull() }
            Staged(name, temp, FileSniffer.kind(head, name, mime))
        } catch (e: IOException) {
            Staged(name, null, FileKind.UNKNOWN, "The file could not be read: ${e.message ?: e.javaClass.simpleName}")
        } catch (e: SecurityException) {
            Staged(name, null, FileKind.UNKNOWN, "StudyPack was not given access to this file. Try importing it again.")
        }
    }

    /** Copies at most [MAX_FILE_BYTES]; returns the size, or -1 if the input is larger. */
    private fun copyLimited(input: InputStream, output: java.io.OutputStream): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return total
            total += read
            if (total > MAX_FILE_BYTES) return -1
            output.write(buffer, 0, read)
        }
    }

    private fun readHead(input: InputStream): ByteArray {
        val buffer = ByteArray(FileSniffer.HEAD_SIZE)
        var filled = 0
        while (filled < buffer.size) {
            val read = input.read(buffer, filled, buffer.size - filled)
            if (read < 0) break
            filled += read
        }
        return buffer.copyOf(filled)
    }

    private fun displayName(uri: Uri): String {
        val queried = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        return queried?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    }

    private suspend fun importPack(staged: Staged): ImportOutcome {
        val file = staged.file ?: return ImportOutcome.Failed(listOf("The file could not be read."))
        if (file.length() > PackParser.MAX_BYTES) {
            return ImportOutcome.Failed(listOf("Packs can be at most ${PackParser.MAX_BYTES / 1024 / 1024} MB. Split it into smaller packs."))
        }
        val result = withContext(io) { PackParser.parse(file.readBytes()) }
        val pack = result.pack ?: return ImportOutcome.Failed(result.errors.map { it.message })
        val info = pack.info
        val warnings = result.warnings.map { it.message }.toMutableList()

        val existing = repository.pack(info.id)
        if (existing != null) {
            val newVersion = PackVersion.parse(info.version)
            val oldVersion = PackVersion.parse(existing.version)
            if (newVersion != null && oldVersion != null && newVersion <= oldVersion) {
                val decision = ImportDecision.ReplacePack(staged.fileName, existing.title, existing.version, info.version, isOlder = newVersion < oldVersion)
                if (!ask(decision, decision.answer)) return ImportOutcome.KeptExisting(existing.version)
            }
            val oldIds = repository.questionIds(info.id).toSet()
            val kept = pack.questions.count { it.id in oldIds }
            if (oldIds.isNotEmpty() && kept * 2 < oldIds.size) {
                warnings += "Only $kept of the ${oldIds.size} question IDs from version ${existing.version} are still in this version, " +
                    "so history for the others is not carried over. Keep question IDs the same between versions."
            }
        }
        repository.savePack(pack)

        val imported = repository.noteFileNames(info.moduleCode).map { it.lowercase() }.toSet()
        val infos = result.infos.map { it.message } +
            info.notes.filter { it.lowercase() !in imported }.map { "Import $it to attach the notes." }
        return ImportOutcome.PackImported(
            title = info.title,
            moduleCode = info.moduleCode,
            questionCount = pack.questions.size,
            topicCount = pack.topics.size,
            version = info.version,
            replacedVersion = existing?.version,
            warnings = warnings,
            infos = infos,
        )
    }

    private suspend fun importPdf(staged: Staged, defaultModule: String?): ImportOutcome {
        val file = staged.file ?: return ImportOutcome.Failed(listOf("The file could not be read."))
        val info = try {
            withContext(io) { pdfs.inspect(file) }
        } catch (e: PdfException) {
            return ImportOutcome.Failed(listOf(e.message.orEmpty()))
        }
        val modules = repository.modules()
        val packs = repository.allPacks().map { PackNotes(it.moduleCode, it.notes) }
        var moduleCode = NoteLinking.moduleFor(staged.fileName, packs, modules.map { it.code }) ?: defaultModule
        var moduleName: String? = null
        if (moduleCode == null) {
            val decision = ImportDecision.ChooseModule(staged.fileName, modules.map { ModuleChoice(it.code, it.name) })
            val choice = ask(decision, decision.answer) ?: return ImportOutcome.Cancelled
            moduleCode = choice.code.trim().uppercase()
            moduleName = choice.name?.trim()?.ifEmpty { null }
            if (moduleCode.isEmpty()) return ImportOutcome.Cancelled
        }

        val existing = repository.noteByName(moduleCode, staged.fileName)
        val path = files.newNotePath()
        withContext(io) { files.store(file, path) }
        val title = NoteTitles.displayTitle(staged.fileName, info.title, moduleCode)
        repository.saveNote(moduleCode, moduleName, staged.fileName, title, path, info.pageCount, replacing = existing)
        if (existing != null) withContext(io) { files.delete(existing.internalPath) }
        return ImportOutcome.NotesImported(title, moduleCode, info.pageCount, replaced = existing != null)
    }

    private suspend fun <T> ask(decision: ImportDecision, answer: CompletableDeferred<T>): T {
        _state.update { it.copy(decision = decision) }
        try {
            return answer.await()
        } finally {
            _state.update { if (it.decision === decision) it.copy(decision = null) else it }
        }
    }

    companion object {
        /** Bundled demo files: the pack lists the PDF in `pack.notes`, so they import as one module. */
        val SAMPLE_FILES = listOf("sample/demo.studypack.json", "sample/DEMO101-circuits-overview.pdf")

        /** Upper limit for any incoming file (notes PDFs included). */
        const val MAX_FILE_BYTES = 200L * 1024 * 1024
    }
}
