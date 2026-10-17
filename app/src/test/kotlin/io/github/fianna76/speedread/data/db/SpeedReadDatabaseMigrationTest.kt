package io.github.fianna76.speedread.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Upgrades a database created from the exported version 1 schema (`app/schemas/`) with the
 * current code (through every migration), proving existing libraries survive the updates for
 * PDF support and the Newsstand.
 */
@RunWith(AndroidJUnit4::class)
class SpeedReadDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    @Test
    fun `version 1 libraries upgrade with books marked as EPUB and no source URL`() = runTest {
        createVersion1Database()

        val database = Room.databaseBuilder(context, SpeedReadDatabase::class.java, name).build()
        try {
            val book = database.bookDao().findById(1)!!
            assertEquals("Old Book", book.title)
            assertEquals("EPUB", book.format)
            assertNull(book.sourceUrl)
            assertEquals(42, book.wordsRead)
            assertEquals(1, database.bookDao().chaptersOf(1).size)
        } finally {
            database.close()
        }
    }

    private fun createVersion1Database() {
        val schema = JSONObject(File(SCHEMA_DIR, "1.json").readText()).getJSONObject("database")
        val path = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL(
                "INSERT INTO books (id, title, author, language, file_name, cover_file_name, content_hash, " +
                    "total_words, added_at, last_opened_at, chapter_index, word_index, words_read) " +
                    "VALUES (1, 'Old Book', NULL, 'en', 'abc.epub', NULL, 'abc', 100, 0, NULL, 0, 42, 42)",
            )
            db.execSQL(
                "INSERT INTO chapters (book_id, chapter_index, title, href, word_count, start_word) " +
                    "VALUES (1, 0, 'One', 'OEBPS/one.xhtml', 100, 0)",
            )
            db.version = 1
        }
    }

    private companion object {
        val SCHEMA_DIR = File("schemas/io.github.fianna76.speedread.data.db.SpeedReadDatabase")
    }
}
