package io.github.fianna76.studypack.ui.rsvp

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.studypack.data.NoteFile
import io.github.fianna76.studypack.data.pdf.NotesTextLoader
import io.github.fianna76.studypack.data.settings.ReadingPreferences
import io.github.fianna76.studypack.testing.MainDispatcherRule
import io.github.fianna76.studypack.testing.TestGraph
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RsvpViewModelTest {

    @get:Rule(order = 0)
    val tmp = TemporaryFolder()

    @get:Rule(order = 1)
    val main = MainDispatcherRule()

    private val graph by lazy { TestGraph(tmp.newFolder("app")) }
    private val preferences by lazy { ReadingPreferences(PreferenceDataStoreFactory.create { File(tmp.root, "settings.preferences_pb") }) }
    private lateinit var note: NoteFile

    @Before
    fun setUp() {
        graph.importer.importSample()
        graph.awaitReports()
        note = runBlocking { graph.repository.observeNotes("DEMO101").first().single() }
    }

    @After
    fun tearDown() = graph.close()

    private fun viewModel(startPage: Int? = null) =
        RsvpViewModel(note.id, startPage, graph.repository, NotesTextLoader(graph.files, graph.pdfs), preferences, graph.scope)

    private fun RsvpViewModel.loaded(): RsvpUiState = runBlocking { withTimeout(20_000) { uiState.first { !it.isLoading } } }

    /** Runs playback (on virtual time) until it stops by itself. */
    private fun playToEnd() = main.dispatcher.scheduler.advanceUntilIdle()

    @Test
    fun firstReadStartsAtSectionOneNotTheTitlePage() {
        val vm = viewModel()
        val ui = vm.loaded()
        assertEquals(1, ui.chapterIndex)
        assertEquals("1. Ohm's law and power", ui.chapter?.title)
        assertEquals(0, vm.playerState.value.index)
        assertEquals(7, ui.chapters.size)
    }

    @Test
    fun startingFromATitleOrContentsPageAlsoSkipsThem() {
        assertEquals(1, viewModel(startPage = 1).loaded().chapterIndex)
        assertEquals("3. RC circuits", viewModel(startPage = 4).loaded().chapter?.title)
    }

    @Test
    fun readingRunsOnThroughSectionsAndStopsBeforeSources() {
        val vm = viewModel()
        vm.loaded()
        vm.selectChapter(4) // 4. Op-amps
        vm.togglePlayPause()
        playToEnd()
        val ui = vm.uiState.value
        assertTrue("Should finish after the formula sheet", ui.isFinished)
        assertEquals("5. Formula sheet", ui.chapter?.title)
        assertFalse(vm.playerState.value.isPlaying)
    }

    @Test
    fun skippedSectionsCanStillBeChosen() {
        val vm = viewModel()
        vm.loaded()
        vm.selectChapter(6) // Sources
        assertEquals("Sources", vm.uiState.value.chapter?.title)
        vm.togglePlayPause()
        playToEnd()
        assertTrue(vm.uiState.value.isFinished)
    }

    @Test
    fun tappedWordIsSavedAndResumedExactly() {
        val vm = viewModel()
        vm.loaded()
        vm.selectChapter(3)
        vm.seekToWord(10)
        val saved = runBlocking {
            withTimeout(10_000) { graph.repository.observeNote(note.id).first { it?.rsvpWord != null && it.rsvpWord > 0 } }!!
        }
        assertEquals(4, saved.lastPage) // section 3 is on page 5
        note = saved

        val resumed = viewModel()
        assertEquals(3, resumed.loaded().chapterIndex)
        assertEquals(10, resumed.playerState.value.index)
    }

    @Test
    fun sectionNavigation() {
        val vm = viewModel()
        vm.loaded()
        vm.nextChapter()
        assertEquals(2, vm.uiState.value.chapterIndex)
        vm.seekToWord(30)
        vm.previousChapter() // well into the section: back to its start
        assertEquals(2, vm.uiState.value.chapterIndex)
        assertEquals(0, vm.playerState.value.index)
        vm.previousChapter() // at its start: the previous section
        assertEquals(1, vm.uiState.value.chapterIndex)
        // "Previous sentence" at the very start of a section goes back into the one before.
        vm.previousSentence()
        assertEquals(0, vm.uiState.value.chapterIndex)
    }

    @Test
    fun speedSliderSnapsToStepsAndIsSaved() = runBlocking {
        val vm = viewModel()
        vm.loaded()
        vm.setWordsPerMinute(437)
        assertEquals(425, vm.playerState.value.settings.wordsPerMinute)
        vm.commitWordsPerMinute()
        vm.changeWordsPerMinuteBy(25)
        assertEquals(450, vm.playerState.value.settings.wordsPerMinute)
        withTimeout(10_000) { preferences.settings.first { it.wordsPerMinute == 450 } }
        vm.setWordsPerMinute(5_000)
        assertEquals(1_000, vm.playerState.value.settings.wordsPerMinute)
    }

    @Test
    fun finishedNotesStartOverNextTime() {
        val vm = viewModel()
        vm.loaded()
        vm.selectChapter(5)
        vm.togglePlayPause()
        playToEnd()
        assertTrue(vm.uiState.value.isFinished)
        note = runBlocking { withTimeout(10_000) { graph.repository.observeNote(note.id).first { it?.rsvpWord == 0 || (it?.rsvpWord ?: -1) in 1..60 } }!! }
        val again = viewModel().loaded()
        assertEquals(1, again.chapterIndex)
        assertFalse(again.isFinished)
    }
}
