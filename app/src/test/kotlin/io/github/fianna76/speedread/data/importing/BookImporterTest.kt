package io.github.fianna76.speedread.data.importing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.speedread.data.BookFileStore
import io.github.fianna76.speedread.data.BookFormat
import io.github.fianna76.speedread.data.pdf.PdfBooks
import io.github.fianna76.speedread.data.pdf.PdfPageRange
import io.github.fianna76.speedread.data.db.SpeedReadDatabase
import io.github.fianna76.speedread.epub.testing.TestEpubs
import io.github.fianna76.speedread.testing.TestPdfs
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

// Native graphics make BitmapFactory behave like a device (Robolectric's legacy shadows
// "decode" any byte array into a placeholder bitmap).
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BookImporterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: SpeedReadDatabase
    private lateinit var files: BookFileStore
    private lateinit var importer: BookImporter

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, SpeedReadDatabase::class.java).build()
        files = BookFileStore(tmp.newFolder("files"))
        importer = BookImporter(
            context.contentResolver,
            files,
            database.bookDao(),
            PdfBooks(context),
            // Robolectric can't run the platform PdfRenderer; a solid page stands in for it.
            pdfCovers = { _, height -> Bitmap.createBitmap(height * 2 / 3, height, Bitmap.Config.ARGB_8888) },
            clock = { 1_000L },
        )
    }

    @After
    fun tearDown() = database.close()

    private fun uriOf(file: File): Uri = Uri.fromFile(file)

    @Test
    fun `imports metadata, chapters and word counts`() = runTest {
        val result = importer.import(uriOf(TestEpubs.epub3(tmp.newFile("book.epub"))))

        val imported = result as ImportResult.Imported
        assertEquals("The Main Title", imported.title)
        val dao = database.bookDao()
        val book = dao.findById(imported.bookId)!!
        assertEquals("Ada Author", book.author)
        assertEquals("en", book.language)
        assertEquals(11, book.totalWords)
        assertEquals(1_000L, book.addedAt)
        assertTrue(files.bookFile(book.fileName).isFile)
        // The "cover" in this fixture is not a decodable image, so no thumbnail is stored.
        assertNull(book.coverFileName)

        val chapters = dao.chaptersOf(book.id)
        assertEquals(listOf("Chapter One", "Chapter Two"), chapters.map { it.title })
        assertEquals(listOf(7, 4), chapters.map { it.wordCount })
        assertEquals(listOf(0, 7), chapters.map { it.startWord })
    }

    @Test
    fun `importing the same file twice is detected`() = runTest {
        val file = TestEpubs.epub3(tmp.newFile("book.epub"))
        val first = importer.import(uriOf(file)) as ImportResult.Imported
        val copy = file.copyTo(File(tmp.root, "copy.epub"))

        val second = importer.import(uriOf(copy))

        assertEquals(ImportResult.AlreadyInLibrary(first.bookId, "The Main Title"), second)
    }

    @Test
    fun `rejects files that are not EPUBs`() = runTest {
        val file = tmp.newFile("notes.epub").apply { writeText("plain text") }
        assertEquals(ImportResult.Failed("notes.epub", ImportError.INVALID_FILE), importer.import(uriOf(file)))
        assertTrue(noFilesLeft())
    }

    @Test
    fun `rejects DRM-protected books`() = runTest {
        val file = TestEpubs.encrypted(tmp.newFile("locked.epub"))
        assertEquals(ImportResult.Failed("locked.epub", ImportError.DRM_PROTECTED), importer.import(uriOf(file)))
    }

    @Test
    fun `rejects books without readable text`() = runTest {
        val file = TestEpubs.simple(tmp.newFile("pictures.epub"), "Pictures", "<div><img src=\"a.png\" alt=\"\"/></div>")
        assertEquals(ImportResult.Failed("pictures.epub", ImportError.NO_TEXT), importer.import(uriOf(file)))
        assertTrue(noFilesLeft())
    }

    @Test
    fun `reports unreadable sources`() = runTest {
        val missing = File(tmp.root, "missing.epub")
        val result = importer.import(uriOf(missing)) as ImportResult.Failed
        assertEquals(ImportError.UNREADABLE, result.reason)
    }

    @Test
    fun `stores a down-scaled cover thumbnail`() = runTest {
        val cover = Bitmap.createBitmap(800, 1200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val png = ByteArrayOutputStream().also { cover.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

        val result = importer.import(uriOf(TestEpubs.epub3(tmp.newFile("covered.epub"), coverBytes = png)))

        val book = database.bookDao().findById((result as ImportResult.Imported).bookId)!!
        val thumbnail = files.coverFile(checkNotNull(book.coverFileName))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(thumbnail.path, bounds)
        assertEquals(600, bounds.outHeight)
        assertEquals(400, bounds.outWidth)
    }

    @Test
    fun `imports a PDF using its outline as chapters`() = runTest {
        val pdf = TestPdfs.write(
            context,
            tmp.newFile("guide.pdf"),
            pages = listOf(
                listOf("Speed Reading Guide"),
                listOf("Chapter one begins here.", "It has two lines."),
                listOf("More of chapter one."),
                listOf("Chapter two is short."),
            ),
            title = "The Guide",
            author = "Pat Writer",
            outline = listOf("One" to 1, "Two" to 3),
        )

        val result = importer.import(uriOf(pdf)) as ImportResult.Imported

        assertEquals("The Guide", result.title)
        val book = database.bookDao().findById(result.bookId)!!
        assertEquals(BookFormat.PDF.name, book.format)
        assertEquals("Pat Writer", book.author)
        assertTrue(book.fileName.endsWith(".pdf"))
        assertTrue(files.coverFile(checkNotNull(book.coverFileName)).isFile)
        val chapters = database.bookDao().chaptersOf(book.id)
        // The untitled front matter before the first bookmark is kept as its own section.
        assertEquals(listOf(null, "One", "Two"), chapters.map { it.title })
        assertEquals(
            listOf(PdfPageRange(0, 1), PdfPageRange(1, 3), PdfPageRange(3, 4)),
            chapters.map { PdfPageRange.fromHref(it.href) },
        )
        assertEquals(listOf(3, 12, 4), chapters.map { it.wordCount })
    }

    @Test
    fun `bookmarks starting on the same page share a section`() = runTest {
        val pdf = TestPdfs.write(
            context,
            tmp.newFile("paper.pdf"),
            pages = listOf(listOf("Abstract text here."), listOf("Methods and more."), listOf("Results text.")),
            outline = listOf("Abstract" to 0, "Introduction" to 1, "Methods" to 1, "Results" to 2),
        )

        val result = importer.import(uriOf(pdf)) as ImportResult.Imported

        assertEquals(
            listOf("Abstract", "Introduction \u00B7 Methods", "Results"),
            database.bookDao().chaptersOf(result.bookId).map { it.title },
        )
    }

    @Test
    fun `PDFs without an outline are read in blocks of pages`() = runTest {
        val pages = List(35) { listOf("This is page number ${it + 1} of the book.") }
        val pdf = TestPdfs.write(context, tmp.newFile("long.pdf"), pages)

        val result = importer.import(uriOf(pdf)) as ImportResult.Imported

        assertEquals("long", result.title) // No document title: falls back to the file name.
        val ranges = database.bookDao().chaptersOf(result.bookId).map { PdfPageRange.fromHref(it.href) }
        assertEquals(
            listOf(PdfPageRange(0, 10), PdfPageRange(10, 20), PdfPageRange(20, 30), PdfPageRange(30, 35)),
            ranges,
        )
    }

    @Test
    fun `rejects password-protected PDFs`() = runTest {
        val pdf = TestPdfs.write(context, tmp.newFile("locked.pdf"), listOf(listOf("Secret")), userPassword = "hunter2")
        assertEquals(ImportResult.Failed("locked.pdf", ImportError.PASSWORD_PROTECTED), importer.import(uriOf(pdf)))
        assertTrue(noFilesLeft())
    }

    @Test
    fun `rejects PDFs without a text layer`() = runTest {
        val pdf = TestPdfs.write(context, tmp.newFile("scan.pdf"), listOf(emptyList(), emptyList()))
        assertEquals(ImportResult.Failed("scan.pdf", ImportError.NO_TEXT), importer.import(uriOf(pdf)))
    }

    @Test
    fun `rejects damaged PDFs`() = runTest {
        val file = tmp.newFile("broken.pdf").apply { writeText("%PDF-1.7\nthis is not really a PDF") }
        assertEquals(ImportResult.Failed("broken.pdf", ImportError.INVALID_FILE), importer.import(uriOf(file)))
    }

    /** True when a failed import left no book, cover or scratch files behind. */
    private fun noFilesLeft(): Boolean {
        val root = File(tmp.root, "files")
        return listOf("books", "covers", "import-tmp", "text-cache").all { File(root, it).listFiles().isNullOrEmpty() }
    }
}
