package io.github.fianna76.studypack.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Upgrades a database built from the exported version 1 schema with the current code. */
@RunWith(AndroidJUnit4::class)
class StudyPackDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    @Test
    fun version1DataUpgradesThroughEveryVersion() = runTest {
        createVersion1Database()
        val database = Room.databaseBuilder(context, StudyPackDatabase::class.java, name).build()
        try {
            val dao = database.libraryDao()
            val note = dao.note(1)!!
            assertEquals("Overview", note.displayTitle)
            assertEquals(3, note.lastPage)
            assertEquals(-1, note.rsvpWord)
            dao.updateReadingPosition(1, page = 4, word = 120)
            assertEquals(120, dao.note(1)!!.rsvpWord)
            assertEquals("EE5105", dao.modules().single().code)
            // 2 → 3: history and results from before "Don't know" have no skips.
            val stat = database.statsDao().stat("ee5105-ecg-amp", "cmrr-001")!!
            assertEquals(2, stat.wrong)
            assertEquals(0, stat.skipped)
            assertEquals(0, database.statsDao().observeSessions("EE5105").first().single().skipped)
        } finally {
            database.close()
        }
    }

    private fun createVersion1Database() {
        val schema = File("schemas/io.github.fianna76.studypack.data.db.StudyPackDatabase/1.json")
        val json = JSONObject(schema.readText()).getJSONObject("database")
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = json.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                entity.optJSONArray("indices")?.let { indices ->
                    for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = json.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("INSERT INTO modules (code, name) VALUES ('EE5105', 'Applied Circuit Design')")
            db.execSQL(
                "INSERT INTO note_files (id, moduleCode, fileName, displayTitle, internalPath, pageCount, lastPage, importedAt) " +
                    "VALUES (1, 'EE5105', 'EE5105-ecg-amp-overview.pdf', 'Overview', 'notes/a.pdf', 20, 3, 1)",
            )
            db.execSQL("INSERT INTO question_stats (packId, questionId, seen, wrong, lastSeenAt, lastCorrect) VALUES ('ee5105-ecg-amp', 'cmrr-001', 3, 2, 5, 0)")
            db.execSQL(
                "INSERT INTO session_results (moduleCode, startedAt, endedAt, mode, examMode, packIds, total, answered, correct, perTopicJson, seed) " +
                    "VALUES ('EE5105', 1, 2, 'WARM_UP', 0, '[\"ee5105-ecg-amp\"]', 12, 12, 9, '[]', 7)",
            )
            db.version = 1
        }
    }
}
