package io.github.fianna76.studypack.importing

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.studypack.quiz.QuestionKey
import io.github.fianna76.studypack.testing.TestGraph
import io.github.fianna76.studypack.testing.TestPacks
import io.github.fianna76.studypack.testing.TestPdfs
import io.github.fianna76.studypack.testing.resource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ImportCoordinatorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val graph by lazy { TestGraph(tmp.newFolder("app")) }
    private val inbox by lazy { tmp.newFolder("inbox") }

    @After
    fun tearDown() = graph.close()

    private fun uri(file: File): Uri = Uri.fromFile(file)

    private fun pack(name: String = "test.studypack.json", content: String = TestPacks.json()) = uri(TestPacks.write(inbox, name, content))

    private fun pdf(name: String, pages: Int = 3, title: String? = null) =
        uri(TestPdfs.write(graph.context, File(inbox, name), List(pages) { listOf("Page ${it + 1} text.") }, title = title))

    private fun importAndReport(vararg uris: Uri, defaultModule: String? = null): List<ImportReport> {
        graph.importer.enqueue(uris.toList(), defaultModule)
        return graph.awaitReports().reports
    }

    @Test
    fun importsAPackUnderItsModule() = runBlocking {
        val report = importAndReport(pack()).single()
        val outcome = report.outcome as ImportOutcome.PackImported
        assertEquals("test.studypack.json", report.fileName)
        assertEquals(4, outcome.questionCount)
        assertEquals(2, outcome.topicCount)
        assertNull(outcome.replacedVersion)
        assertEquals(listOf("Import TEST101-test-overview.pdf to attach the notes."), outcome.infos)
        val module = graph.repository.observeModules().first().single()
        assertEquals("TEST101", module.code)
        assertEquals("Module TEST101", module.name)
        assertEquals(1, module.packCount)
        assertEquals(4, module.questionCount)
    }

    @Test
    fun importsTheRealEcgPack() = runBlocking {
        val file = File(inbox, "EE5105-ecg-amp.studypack.json").apply { writeBytes(resource("EE5105-ecg-amp.studypack.json")) }
        val outcome = importAndReport(uri(file)).single().outcome as ImportOutcome.PackImported
        assertEquals(183, outcome.questionCount)
        assertEquals(10, outcome.topicCount)
        val content = graph.repository.quizContent(listOf("ee5105-ecg-amp"))
        assertEquals(183, content.questions.size)
        assertEquals("topology-gain-001", content.questions.first().id)
    }

    @Test
    fun invalidPackIsRefusedNamingTheQuestion() = runBlocking {
        val outcome = importAndReport(pack(content = TestPacks.json(brokenTopicFor = "b-2"))).single().outcome
        val messages = (outcome as ImportOutcome.Failed).messages
        assertTrue(messages.single(), messages.single().contains("Question b-2"))
        assertTrue(graph.repository.observeModules().first().isEmpty())
    }

    @Test
    fun anythingElseIsRefused() {
        val text = uri(TestPacks.write(inbox, "notes.txt", "just some text"))
        val outcome = importAndReport(text).single().outcome as ImportOutcome.Failed
        assertTrue(outcome.messages.single().contains("neither a question pack"))
    }

    @Test
    fun packWithoutJsonExtensionIsSniffed() {
        val outcome = importAndReport(pack(name = "download.bin")).single().outcome
        assertTrue(outcome is ImportOutcome.PackImported)
    }

    @Test
    fun notesListedInAPackAttachToItsModuleEvenWhenPickedFirst() = runBlocking {
        // A file name the module-code rule would not match, so only pack.notes can link it.
        val notes = pdf("overview-notes.pdf", title = "ECG overview")
        val reports = importAndReport(notes, pack(content = TestPacks.json(notes = listOf("overview-notes.pdf"))))
        assertEquals("Packs are imported first", "test.studypack.json", reports.first().fileName)
        val outcome = reports.last().outcome as ImportOutcome.NotesImported
        assertEquals("TEST101", outcome.moduleCode)
        assertEquals(3, outcome.pageCount)
        val note = graph.repository.observeNotes("TEST101").first().single()
        assertEquals("ECG overview", note.displayTitle)
        assertTrue(graph.files.file(note.internalPath).isFile)
        // The pack went first, so at that point it still suggested importing them.
        assertEquals(listOf("Import overview-notes.pdf to attach the notes."), (reports.first().outcome as ImportOutcome.PackImported).infos)
    }

    @Test
    fun notesNamedAfterAKnownModuleAttachToIt() = runBlocking {
        importAndReport(pack(content = TestPacks.json(notes = emptyList())))
        val outcome = importAndReport(pdf("test101-lab-2-overview.pdf")).single().outcome as ImportOutcome.NotesImported
        assertEquals("TEST101", outcome.moduleCode)
        assertEquals("Lab 2 overview", graph.repository.observeNotes("TEST101").first().single().displayTitle)
    }

    @Test
    fun unknownNotesAskForAModule() = runBlocking {
        graph.importer.enqueue(listOf(pdf("random.pdf")))
        val decision = graph.awaitDecision().decision as ImportDecision.ChooseModule
        assertEquals("random.pdf", decision.fileName)
        assertTrue(decision.modules.isEmpty())
        graph.importer.chooseModule(decision, ModuleChoice("ab1234", "Anatomy"))
        val outcome = graph.awaitReports().reports.single().outcome as ImportOutcome.NotesImported
        assertEquals("AB1234", outcome.moduleCode)
        assertEquals("Anatomy", graph.repository.observeModules().first().single().name)
    }

    @Test
    fun cancellingTheModuleChoiceImportsNothing() = runBlocking {
        graph.importer.enqueue(listOf(pdf("random.pdf")))
        graph.importer.chooseModule(graph.awaitDecision().decision as ImportDecision.ChooseModule, null)
        assertEquals(ImportOutcome.Cancelled, graph.awaitReports().reports.single().outcome)
        assertTrue(graph.repository.observeModules().first().isEmpty())
    }

    @Test
    fun defaultModuleIsUsedForUnmatchedNotes() = runBlocking {
        importAndReport(pack(content = TestPacks.json(notes = emptyList())))
        val outcome = importAndReport(pdf("random.pdf"), defaultModule = "TEST101").single().outcome as ImportOutcome.NotesImported
        assertEquals("TEST101", outcome.moduleCode)
    }

    @Test
    fun brokenPdfIsRefused() {
        val fake = uri(TestPacks.write(inbox, "broken.pdf", "%PDF-1.7 this is not really a pdf"))
        val outcome = importAndReport(fake).single().outcome as ImportOutcome.Failed
        assertTrue(outcome.messages.single().contains("not a readable PDF"))
    }

    @Test
    fun passwordProtectedPdfIsRefused() {
        val locked = TestPdfs.write(graph.context, File(inbox, "locked.pdf"), listOf(listOf("Secret")), userPassword = "pw")
        val outcome = importAndReport(uri(locked)).single().outcome as ImportOutcome.Failed
        assertTrue(outcome.messages.single().contains("password-protected"))
    }

    @Test
    fun reimportingNotesReplacesTheFileAndKeepsThePlace() = runBlocking {
        importAndReport(pack())
        importAndReport(pdf("TEST101-test-overview.pdf", pages = 5))
        val first = graph.repository.observeNotes("TEST101").first().single()
        graph.repository.updateLastPage(first.id, 3)
        val outcome = importAndReport(pdf("TEST101-test-overview.pdf", pages = 4)).single().outcome as ImportOutcome.NotesImported
        assertTrue(outcome.replaced)
        val second = graph.repository.observeNotes("TEST101").first().single()
        assertEquals(first.id, second.id)
        assertEquals(3, second.lastPage)
        assertEquals(4, second.pageCount)
        assertTrue(!graph.files.file(first.internalPath).exists())
    }

    @Test
    fun newerVersionReplacesSilentlyAndKeepsHistory() = runBlocking {
        importAndReport(pack())
        val key = QuestionKey("test-pack", "a-1")
        graph.repository.recordAnswer(key, correct = false, tutorMode = true)
        val v2 = TestPacks.json(version = "1.1.0", questionIds = listOf("a-1", "a-2", "b-1", "b-3"))
        val outcome = importAndReport(pack(content = v2)).single().outcome as ImportOutcome.PackImported
        assertEquals("1.0.0", outcome.replacedVersion)
        assertTrue(outcome.warnings.isEmpty())
        assertEquals(1, graph.repository.history(listOf("test-pack"))[key]?.wrong)
        assertEquals(listOf("a-1", "a-2", "b-1", "b-3"), graph.repository.quizContent(listOf("test-pack")).questions.map { it.id })
    }

    @Test
    fun sameVersionAsksAndCanBeKept() = runBlocking {
        importAndReport(pack())
        graph.importer.enqueue(listOf(pack(content = TestPacks.json(questionIds = listOf("a-1")))))
        val decision = graph.awaitDecision().decision as ImportDecision.ReplacePack
        assertEquals("1.0.0", decision.currentVersion)
        assertTrue(!decision.isOlder)
        graph.importer.replace(decision, false)
        assertEquals(ImportOutcome.KeptExisting("1.0.0"), graph.awaitReports().reports.single().outcome)
        assertEquals(4, graph.repository.quizContent(listOf("test-pack")).questions.size)
    }

    @Test
    fun olderVersionCanBeForcedAndWarnsAboutLostIds() = runBlocking {
        importAndReport(pack(content = TestPacks.json(version = "2.0.0")))
        graph.importer.enqueue(listOf(pack(content = TestPacks.json(version = "1.0.0", questionIds = listOf("a-1", "a-9", "b-8", "b-9")))))
        val decision = graph.awaitDecision().decision as ImportDecision.ReplacePack
        assertTrue(decision.isOlder)
        graph.importer.replace(decision, true)
        val outcome = graph.awaitReports().reports.single().outcome as ImportOutcome.PackImported
        assertEquals("2.0.0", outcome.replacedVersion)
        assertTrue(outcome.warnings.single().contains("Only 1 of the 4 question IDs from version 2.0.0"))
    }

    @Test
    fun sampleImportsAsOneModule() = runBlocking {
        graph.importer.importSample()
        val reports = graph.awaitReports().reports
        assertEquals(2, reports.size)
        assertTrue(reports[0].outcome is ImportOutcome.PackImported)
        assertEquals("DEMO101", (reports[1].outcome as ImportOutcome.NotesImported).moduleCode)
        val module = graph.repository.observeModules().first().single()
        assertEquals(1, module.packCount)
        assertEquals(1, module.noteCount)
        assertNotNull(module.name)
    }
}
