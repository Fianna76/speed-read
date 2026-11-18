package io.github.fianna76.studypack.importing

import kotlinx.coroutines.CompletableDeferred

/** An existing module, or a new one typed in by the user. */
data class ModuleChoice(val code: String, val name: String?)

/** A question the import needs the user to answer before it can go on. */
sealed interface ImportDecision {
    val fileName: String

    /** The pack is already imported with the same or a higher version. */
    class ReplacePack(
        override val fileName: String,
        val title: String,
        val currentVersion: String,
        val newVersion: String,
        val isOlder: Boolean,
        internal val answer: CompletableDeferred<Boolean> = CompletableDeferred(),
    ) : ImportDecision

    /** A notes PDF matches no module: pick one or create one (`null` cancels). */
    class ChooseModule(
        override val fileName: String,
        val modules: List<ModuleChoice>,
        internal val answer: CompletableDeferred<ModuleChoice?> = CompletableDeferred(),
    ) : ImportDecision
}

sealed interface ImportOutcome {

    data class PackImported(
        val title: String,
        val moduleCode: String,
        val questionCount: Int,
        val topicCount: Int,
        val version: String,
        /** The version that was replaced, or `null` for a new pack. */
        val replacedVersion: String?,
        val warnings: List<String>,
        val infos: List<String>,
    ) : ImportOutcome

    data class NotesImported(
        val title: String,
        val moduleCode: String,
        val pageCount: Int,
        val replaced: Boolean,
    ) : ImportOutcome

    /** The user chose to keep the imported version. */
    data class KeptExisting(val version: String) : ImportOutcome

    /** The user cancelled (for example when asked for a module). */
    data object Cancelled : ImportOutcome

    data class Failed(val messages: List<String>) : ImportOutcome
}

data class ImportReport(val fileName: String, val outcome: ImportOutcome)

data class ImportState(
    val running: Boolean = false,
    val decision: ImportDecision? = null,
    /** Results of finished imports, shown until dismissed. */
    val reports: List<ImportReport> = emptyList(),
)
