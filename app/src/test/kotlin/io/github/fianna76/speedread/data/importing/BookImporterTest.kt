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
import io.github.fianna76.speedread.data.db.SpeedReadDatabase
import io.github.fianna76.speedread.epub.testing.TestEpubs
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
        importer = BookImporter(context.contentResolver, files, database.bookDao(), clock = { 1_000L })
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
        assertEquals(ImportResult.Failed("notes.epub", ImportError.NOT_AN_EPUB), importer.import(uriOf(file)))
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

    /** True when a failed import left no book, cover or scratch files behind. */
    private fun noFilesLeft(): Boolean {
        val root = File(tmp.root, "files")
        return listOf("books", "covers", "import-tmp").all { File(root, it).listFiles().isNullOrEmpty() }
    }
}
