package io.github.fianna76.speedread.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.speedread.data.BookFileStore
import io.github.fianna76.speedread.data.OfflineBookRepository
import io.github.fianna76.speedread.data.ReadingPosition
import io.github.fianna76.speedread.data.db.SpeedReadDatabase
import io.github.fianna76.speedread.data.importing.BookImporter
import io.github.fianna76.speedread.data.importing.ImportResult
import io.github.fianna76.speedread.data.pdf.PdfBooks
import io.github.fianna76.speedread.data.settings.UserPreferencesRepository
import io.github.fianna76.speedread.news.NewsRegion
import io.github.fianna76.speedread.epub.testing.TestEpubs
import io.github.fianna76.speedread.testing.TestNews
import io.github.fianna76.speedread.testing.testPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class LibraryBackupTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databases = mutableListOf<SpeedReadDatabase>()

    /** One device's worth of storage. */
    private inner class Device(scope: TestScope) {
        val database = Room.inMemoryDatabaseBuilder(context, SpeedReadDatabase::class.java).build().also { databases += it }
        val filesDir: File = tmp.newFolder()
        val files = BookFileStore(filesDir)
        val preferences: UserPreferencesRepository = testPreferences(tmp.newFolder(), scope.backgroundScope)
        val books = OfflineBookRepository(
            database.bookDao(), files, PdfBooks(context),
            bookmarks = database.bookmarkDao(), collections = database.collectionDao(),
        )
        val backup = LibraryBackup(database.bookDao(), database.bookmarkDao(), database.collectionDao(), files, preferences)

        suspend fun import(): Long {
            val importer = BookImporter(context.contentResolver, files, database.bookDao(), PdfBooks(context), pdfCovers = { _, _ -> null })
            val epub = TestEpubs.epub3(tmp.newFile("book-${filesDir.name}.epub"))
            return (importer.import(Uri.fromFile(epub)) as ImportResult.Imported).bookId
        }
    }

    @After
    fun tearDown() = databases.forEach { it.close() }

    @Test
    fun `a backup restores books, positions, bookmarks, collections and settings on another device`() = runTest {
        val phone = Device(this)
        val id = phone.import()
        phone.books.savePosition(id, ReadingPosition(1, 2), wordsRead = 9)
        phone.books.addBookmark(id, ReadingPosition(1, 0), "Second chapter")
        phone.books.createCollection("Book club", setOf(id))
        phone.preferences.setWordsPerMinute(450)
        phone.preferences.addCustomNewsSource(TestNews.source("custom-1").copy(region = NewsRegion.IRELAND))

        val out = ByteArrayOutputStream()
        assertEquals(BackupSummary(1, 1, 1), phone.backup.export(out))

        val tablet = Device(this)
        assertEquals(BackupSummary(1, 1, 1), tablet.backup.restore(ByteArrayInputStream(out.toByteArray())))

        val book = tablet.books.observeBooks().first().single()
        assertEquals("The Main Title", book.title)
        assertEquals(ReadingPosition(1, 2), book.position)
        assertEquals(9, book.wordsRead)
        val chapters = tablet.books.getChapters(book.id)
        assertEquals(2, chapters.size)
        assertTrue(tablet.books.loadChapterText(book.id, chapters[0]).size > 0) // The book file came along.
        assertEquals(listOf("Second chapter"), tablet.books.observeBookmarks(book.id).first().map { it.snippet })
        assertEquals(setOf(book.id), tablet.books.observeCollections().first().single().bookIds)
        val prefs = tablet.preferences.preferences.first()
        assertEquals(450, prefs.playback.wordsPerMinute)
        assertEquals("custom-1", prefs.customNewsSources.single().id)
    }

    @Test
    fun `restoring merges - nothing is duplicated and the furthest position wins`() = runTest {
        val phone = Device(this)
        val id = phone.import()
        phone.books.savePosition(id, ReadingPosition(0, 1), wordsRead = 1)
        phone.books.addBookmark(id, ReadingPosition(0, 1), "here")
        phone.books.createCollection("Book club", setOf(id))
        val early = ByteArrayOutputStream().also { phone.backup.export(it) }.toByteArray()
        phone.books.savePosition(id, ReadingPosition(1, 3), wordsRead = 10)

        assertEquals(BackupSummary(0, 0, 0), phone.backup.restore(ByteArrayInputStream(early)))

        assertEquals(10, phone.books.observeBooks().first().single().wordsRead) // Not rolled back.
        assertEquals(1, phone.books.observeBookmarks(id).first().size)
        assertEquals(1, phone.books.observeCollections().first().size)
    }

    @Test
    fun `files that are not backups, or try to escape, are rejected or ignored`() = runTest {
        val device = Device(this)
        val notZip = runCatching { device.backup.restore(ByteArrayInputStream("hello".toByteArray())) }.exceptionOrNull()
        assertTrue(notZip is BackupFormatException)

        val hostile = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("../evil.txt"))
                zip.write("x".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("books/.."))
                zip.write("x".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(
                    (
                        "{\"version\":1,\"exportedAt\":0,\"books\":[{\"title\":\"x\",\"author\":null,\"language\":null," +
                            "\"format\":\"EPUB\",\"fileName\":\"..\",\"coverFileName\":null,\"contentHash\":\"h\",\"totalWords\":1," +
                            "\"addedAt\":0,\"lastOpenedAt\":null,\"chapterIndex\":0,\"wordIndex\":0,\"wordsRead\":0,\"chapters\":[]}]}"
                        ).toByteArray(),
                )
                zip.closeEntry()
            }
        }.toByteArray()
        assertEquals(BackupSummary(0, 0, 0), device.backup.restore(ByteArrayInputStream(hostile)))
        assertFalse(File(device.filesDir.parentFile, "evil.txt").exists())
        assertTrue(device.books.observeBooks().first().isEmpty())
    }
}
