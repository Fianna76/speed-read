package io.github.fianna76.speedread.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.speedread.data.db.SpeedReadDatabase
import io.github.fianna76.speedread.data.importing.BookImporter
import io.github.fianna76.speedread.data.importing.ImportResult
import io.github.fianna76.speedread.data.pdf.PdfBooks
import io.github.fianna76.speedread.epub.testing.TestEpubs
import io.github.fianna76.speedread.testing.TestPdfs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OfflineBookRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: SpeedReadDatabase
    private lateinit var files: BookFileStore
    private lateinit var repository: OfflineBookRepository
    private lateinit var importer: BookImporter

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, SpeedReadDatabase::class.java).build()
        files = BookFileStore(tmp.newFolder("files"))
        val pdfBooks = PdfBooks(context)
        repository = OfflineBookRepository(database.bookDao(), files, pdfBooks, clock = { 5_000L })
        importer = BookImporter(context.contentResolver, files, database.bookDao(), pdfBooks, pdfCovers = { _, _ -> null })
    }

    @After
    fun tearDown() = database.close()

    private suspend fun importSample(): Long {
        val file = TestEpubs.epub3(tmp.newFile("book.epub"))
        return (importer.import(Uri.fromFile(file)) as ImportResult.Imported).bookId
    }

    @Test
    fun `exposes imported books with their progress`() = runTest {
        val id = importSample()

        val book = repository.observeBooks().first().single()
        assertEquals(id, book.id)
        assertEquals("The Main Title", book.title)
        assertEquals(0f, book.progress)
        assertNull(book.lastOpenedAt)

        repository.savePosition(id, ReadingPosition(chapterIndex = 1, wordIndex = 2), wordsRead = 9)
        repository.markOpened(id)
        val updated = repository.getBook(id)!!
        assertEquals(ReadingPosition(1, 2), updated.position)
        assertEquals(9f / 11f, updated.progress)
        assertEquals(5_000L, updated.lastOpenedAt)
    }

    @Test
    fun `continue reading finds the last opened book until it is finished`() = runTest {
        val id = importSample()
        assertNull(repository.lastUnfinishedBookId()) // Never opened.

        repository.markOpened(id)
        assertEquals(id, repository.lastUnfinishedBookId())

        repository.savePosition(id, ReadingPosition(chapterIndex = 1, wordIndex = 4), wordsRead = 11)
        assertNull(repository.lastUnfinishedBookId())
    }

    @Test
    fun `loads and tokenises chapter text from the stored file`() = runTest {
        val id = importSample()
        val chapters = repository.getChapters(id)

        val text = repository.loadChapterText(id, chapters[0])

        assertEquals(listOf("Chapter", "One", "It", "was", "a", "dark", "night."), text.words.map { it.text })
        assertEquals(chapters[0].wordCount, text.size)
    }

    @Test
    fun `loads PDF chapter text for its page range`() = runTest {
        val pdf = TestPdfs.write(
            context,
            tmp.newFile("doc.pdf"),
            pages = listOf(listOf("Intro page."), listOf("Body text starts", "on this page.")),
            outline = listOf("Intro" to 0, "Body" to 1),
        )
        val id = (importer.import(Uri.fromFile(pdf)) as ImportResult.Imported).bookId
        val chapters = repository.getChapters(id)
        assertEquals(1..1, chapters[1].pageRange)
        assertEquals(BookFormat.PDF, repository.getBook(id)!!.format)

        val text = repository.loadChapterText(id, chapters[1])
        assertEquals(listOf("Body", "text", "starts", "on", "this", "page."), text.words.map { it.text })

        // Text extracted at import is cached, so reading never has to re-parse the PDF.
        files.bookFile(database.bookDao().findById(id)!!.fileName).delete()
        assertEquals(text.words, repository.loadChapterText(id, chapters[1]).words)
    }

    @Test
    fun `deleting a book removes its rows and files`() = runTest {
        val id = importSample()
        val fileName = database.bookDao().findById(id)!!.fileName
        assertTrue(files.bookFile(fileName).exists())

        repository.deleteBooks(setOf(id))

        assertTrue(repository.observeBooks().first().isEmpty())
        assertTrue(repository.getChapters(id).isEmpty())
        assertFalse(files.bookFile(fileName).exists())
        assertFalse(File(tmp.root, "files/books/$fileName").exists())
    }
}
