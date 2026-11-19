package io.github.fianna76.studypack.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A module row with its counts and latest score, for the library list. */
data class ModuleSummaryRow(
    val code: String,
    val name: String?,
    val packCount: Int,
    val noteCount: Int,
    val questionCount: Int,
    val lastCorrect: Int?,
    val lastAnswered: Int?,
)

data class PackSummaryRow(
    val id: String,
    val moduleCode: String,
    val title: String,
    val version: String,
    val description: String?,
    val questionCount: Int,
)

@Dao
interface LibraryDao {

    @Query(
        """
        SELECT m.code, m.name,
            (SELECT COUNT(*) FROM packs p WHERE p.moduleCode = m.code) AS packCount,
            (SELECT COUNT(*) FROM note_files n WHERE n.moduleCode = m.code) AS noteCount,
            (SELECT COUNT(*) FROM questions q JOIN packs p ON q.packId = p.id WHERE p.moduleCode = m.code) AS questionCount,
            (SELECT s.correct FROM session_results s WHERE s.moduleCode = m.code AND s.answered > 0
                ORDER BY s.endedAt DESC, s.id DESC LIMIT 1) AS lastCorrect,
            (SELECT s.answered FROM session_results s WHERE s.moduleCode = m.code AND s.answered > 0
                ORDER BY s.endedAt DESC, s.id DESC LIMIT 1) AS lastAnswered
        FROM modules m
        WHERE EXISTS (SELECT 1 FROM packs p WHERE p.moduleCode = m.code)
            OR EXISTS (SELECT 1 FROM note_files n WHERE n.moduleCode = m.code)
        ORDER BY m.code COLLATE NOCASE
        """,
    )
    fun observeModuleSummaries(): Flow<List<ModuleSummaryRow>>

    @Query("SELECT * FROM modules WHERE code = :code")
    fun observeModule(code: String): Flow<ModuleEntity?>

    @Query("SELECT * FROM modules WHERE code = :code")
    suspend fun module(code: String): ModuleEntity?

    @Query("SELECT * FROM modules ORDER BY code COLLATE NOCASE")
    suspend fun modules(): List<ModuleEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertModuleIfAbsent(module: ModuleEntity)

    @Query("UPDATE modules SET name = :name WHERE code = :code")
    suspend fun updateModuleName(code: String, name: String)

    /** Creates the module, or fills in its name if it had none and [name] is known. */
    @Transaction
    suspend fun ensureModule(code: String, name: String?) {
        insertModuleIfAbsent(ModuleEntity(code, name))
        if (name != null && module(code)?.name == null) updateModuleName(code, name)
    }

    @Query(
        """
        DELETE FROM modules WHERE code = :code
            AND NOT EXISTS (SELECT 1 FROM packs WHERE moduleCode = :code)
            AND NOT EXISTS (SELECT 1 FROM note_files WHERE moduleCode = :code)
        """,
    )
    suspend fun deleteModuleIfEmpty(code: String)

    // Packs

    @Query(
        """
        SELECT p.id, p.moduleCode, p.title, p.version, p.description,
            (SELECT COUNT(*) FROM questions q WHERE q.packId = p.id) AS questionCount
        FROM packs p WHERE p.moduleCode = :moduleCode ORDER BY p.title COLLATE NOCASE
        """,
    )
    fun observePacks(moduleCode: String): Flow<List<PackSummaryRow>>

    @Query("SELECT * FROM packs WHERE id = :id")
    suspend fun pack(id: String): PackEntity?

    @Query("SELECT * FROM packs WHERE moduleCode = :moduleCode ORDER BY title COLLATE NOCASE")
    suspend fun packsInModule(moduleCode: String): List<PackEntity>

    @Query("SELECT * FROM packs")
    suspend fun allPacks(): List<PackEntity>

    @Query("SELECT id FROM questions WHERE packId = :packId")
    suspend fun questionIds(packId: String): List<String>

    @Query("SELECT * FROM topics WHERE packId IN (:packIds) ORDER BY packId, ordinal")
    suspend fun topics(packIds: Collection<String>): List<TopicEntity>

    @Query("SELECT * FROM questions WHERE packId IN (:packIds) ORDER BY packId, ordinal")
    suspend fun questions(packIds: Collection<String>): List<QuestionEntity>

    @Insert
    suspend fun insertPack(pack: PackEntity)

    @Insert
    suspend fun insertTopics(topics: List<TopicEntity>)

    @Insert
    suspend fun insertQuestions(questions: List<QuestionEntity>)

    @Query("DELETE FROM questions WHERE packId = :packId")
    suspend fun deleteQuestions(packId: String)

    @Query("DELETE FROM topics WHERE packId = :packId")
    suspend fun deleteTopics(packId: String)

    @Query("DELETE FROM packs WHERE id = :packId")
    suspend fun deletePackRow(packId: String)

    @Query("DELETE FROM question_stats WHERE packId = :packId")
    suspend fun deleteStats(packId: String)

    /**
     * Stores a pack, replacing any earlier version with the same ID. History ([QuestionStatEntity])
     * is untouched, so it carries over for question IDs that still exist.
     */
    @Transaction
    suspend fun replacePack(moduleName: String?, pack: PackEntity, topics: List<TopicEntity>, questions: List<QuestionEntity>) {
        val previous = pack(pack.id)
        removePackRows(pack.id)
        ensureModule(pack.moduleCode, moduleName)
        insertPack(pack)
        insertTopics(topics)
        insertQuestions(questions)
        if (previous != null && previous.moduleCode != pack.moduleCode) deleteModuleIfEmpty(previous.moduleCode)
    }

    /**
     * Deletes a pack, its questions, its answer history and the quiz scores that were only about
     * it (scores from sessions that mixed it with packs still imported are kept).
     */
    @Transaction
    suspend fun deletePack(packId: String) {
        val pack = pack(packId) ?: return
        removePackRows(packId)
        deleteStats(packId)
        deleteOrphanedSessions(pack.moduleCode)
        deleteModuleIfEmpty(pack.moduleCode)
    }

    /** Removes the module's session results whose packs have all been deleted. */
    @Transaction
    suspend fun deleteOrphanedSessions(moduleCode: String) {
        val remaining = packsInModule(moduleCode).mapTo(HashSet()) { it.id }
        val orphaned = sessionsInModule(moduleCode).filter { session -> session.packIds.none { it in remaining } }
        if (orphaned.isNotEmpty()) deleteSessions(orphaned.map { it.id })
    }

    /**
     * Deletes a whole module: its packs, questions, answer history, notes and quiz scores.
     * Returns the notes' file paths, for the caller to delete the files.
     */
    @Transaction
    suspend fun deleteModule(moduleCode: String): List<String> {
        packsInModule(moduleCode).forEach { pack ->
            removePackRows(pack.id)
            deleteStats(pack.id)
        }
        val notePaths = notesInModule(moduleCode).map { it.internalPath }
        deleteNotesInModule(moduleCode)
        deleteSessionsInModule(moduleCode)
        deleteModuleRow(moduleCode)
        return notePaths
    }

    @Query("SELECT * FROM session_results WHERE moduleCode = :moduleCode")
    suspend fun sessionsInModule(moduleCode: String): List<SessionResultEntity>

    @Query("DELETE FROM session_results WHERE id IN (:ids)")
    suspend fun deleteSessions(ids: Collection<Long>)

    @Query("DELETE FROM session_results WHERE moduleCode = :moduleCode")
    suspend fun deleteSessionsInModule(moduleCode: String)

    @Query("DELETE FROM modules WHERE code = :moduleCode")
    suspend fun deleteModuleRow(moduleCode: String)

    /** Deletes a pack's rows; children explicitly, rather than relying on foreign-key cascades. */
    @Transaction
    suspend fun removePackRows(packId: String) {
        deleteQuestions(packId)
        deleteTopics(packId)
        deletePackRow(packId)
    }

    // Notes

    @Query("SELECT * FROM note_files WHERE moduleCode = :moduleCode ORDER BY displayTitle COLLATE NOCASE")
    fun observeNotes(moduleCode: String): Flow<List<NoteFileEntity>>

    @Query("SELECT * FROM note_files WHERE id = :id")
    fun observeNote(id: Long): Flow<NoteFileEntity?>

    @Query("SELECT * FROM note_files WHERE id = :id")
    suspend fun note(id: Long): NoteFileEntity?

    @Query("SELECT * FROM note_files WHERE moduleCode = :moduleCode AND fileName = :fileName COLLATE NOCASE LIMIT 1")
    suspend fun noteByName(moduleCode: String, fileName: String): NoteFileEntity?

    @Query("SELECT * FROM note_files WHERE moduleCode = :moduleCode")
    suspend fun notesInModule(moduleCode: String): List<NoteFileEntity>

    @Query("DELETE FROM note_files WHERE moduleCode = :moduleCode")
    suspend fun deleteNotesInModule(moduleCode: String)

    @Query("SELECT fileName FROM note_files WHERE moduleCode = :moduleCode")
    suspend fun noteFileNames(moduleCode: String): List<String>

    @Insert
    suspend fun insertNoteRow(note: NoteFileEntity): Long

    @Upsert
    suspend fun upsertNoteRow(note: NoteFileEntity): Long

    @Transaction
    suspend fun saveNote(moduleName: String?, note: NoteFileEntity): Long {
        ensureModule(note.moduleCode, moduleName)
        if (note.id == 0L) return insertNoteRow(note)
        upsertNoteRow(note)
        return note.id
    }

    @Query("UPDATE note_files SET lastPage = :page WHERE id = :id")
    suspend fun updateLastPage(id: Long, page: Int)

    @Query("DELETE FROM note_files WHERE id = :id")
    suspend fun deleteNoteRow(id: Long)

    @Transaction
    suspend fun deleteNote(id: Long) {
        val note = note(id) ?: return
        deleteNoteRow(id)
        deleteModuleIfEmpty(note.moduleCode)
    }
}

@Dao
interface StatsDao {

    @Query("SELECT * FROM question_stats WHERE packId IN (:packIds)")
    suspend fun stats(packIds: Collection<String>): List<QuestionStatEntity>

    @Query("SELECT * FROM question_stats WHERE packId IN (:packIds)")
    fun observeStats(packIds: Collection<String>): Flow<List<QuestionStatEntity>>

    @Query("SELECT * FROM question_stats WHERE packId = :packId AND questionId = :questionId")
    suspend fun stat(packId: String, questionId: String): QuestionStatEntity?

    @Upsert
    suspend fun upsertStat(stat: QuestionStatEntity)

    @Insert
    suspend fun insertSession(session: SessionResultEntity): Long

    @Query("SELECT * FROM session_results WHERE moduleCode = :moduleCode ORDER BY endedAt DESC, id DESC")
    fun observeSessions(moduleCode: String): Flow<List<SessionResultEntity>>
}
