package io.github.fianna76.studypack.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A module (for example EE5105). Packs and notes are grouped under it in the library. */
@Entity(tableName = "modules")
data class ModuleEntity(
    @PrimaryKey val code: String,
    val name: String?,
)

@Entity(
    tableName = "packs",
    foreignKeys = [ForeignKey(entity = ModuleEntity::class, parentColumns = ["code"], childColumns = ["moduleCode"])],
    indices = [Index("moduleCode")],
)
data class PackEntity(
    @PrimaryKey val id: String,
    val moduleCode: String,
    val title: String,
    val version: String,
    val created: String,
    val description: String?,
    /** File names of the companion notes PDFs (`pack.notes`), to attach them on import. */
    val notes: List<String>,
    val importedAt: Long,
)

@Entity(
    tableName = "topics",
    primaryKeys = ["packId", "id"],
    foreignKeys = [ForeignKey(entity = PackEntity::class, parentColumns = ["id"], childColumns = ["packId"], onDelete = ForeignKey.CASCADE)],
)
data class TopicEntity(
    val packId: String,
    val id: String,
    val name: String,
    val description: String?,
    /** Position in the pack file, so chips keep the author's order. */
    val ordinal: Int,
)

@Entity(
    tableName = "questions",
    primaryKeys = ["packId", "id"],
    foreignKeys = [ForeignKey(entity = PackEntity::class, parentColumns = ["id"], childColumns = ["packId"], onDelete = ForeignKey.CASCADE)],
)
data class QuestionEntity(
    val packId: String,
    val id: String,
    /** Position in the pack file: sessions are built from a stable pool order, so seeds reproduce. */
    val ordinal: Int,
    val topicId: String,
    val type: String,
    val difficulty: Int,
    val prompt: String,
    val correct: String,
    val distractors: List<String>,
    val explanation: String,
    val modelAnswer: String?,
    val sayIt: String?,
    val variantGroup: String?,
    val source: String?,
    val tags: List<String>,
)

@Entity(
    tableName = "note_files",
    foreignKeys = [ForeignKey(entity = ModuleEntity::class, parentColumns = ["code"], childColumns = ["moduleCode"])],
    indices = [Index("moduleCode")],
)
data class NoteFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val moduleCode: String,
    /** The original file name, used to match `pack.notes`. */
    val fileName: String,
    val displayTitle: String,
    /** Path relative to the app's files directory. */
    val internalPath: String,
    val pageCount: Int,
    /** Zero-based page the viewer was last on. */
    val lastPage: Int,
    val importedAt: Long,
)

/**
 * Answer history for one question. Deliberately not linked to [QuestionEntity] by a foreign key,
 * so history survives re-importing a pack; it is removed when the pack is deleted.
 */
@Entity(tableName = "question_stats", primaryKeys = ["packId", "questionId"])
data class QuestionStatEntity(
    val packId: String,
    val questionId: String,
    val seen: Int,
    val wrong: Int,
    val lastSeenAt: Long?,
    val lastCorrect: Boolean?,
)

/** A finished (or ended early) quiz, for "last score" and "best score". */
@Entity(tableName = "session_results", indices = [Index("moduleCode")])
data class SessionResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val moduleCode: String,
    val startedAt: Long,
    val endedAt: Long,
    /** The [io.github.fianna76.studypack.quiz.QuizLength] name, or `RETRY`. */
    val mode: String,
    val examMode: Boolean,
    val packIds: List<String>,
    val total: Int,
    val answered: Int,
    val correct: Int,
    /** Per-topic scores as JSON (`[{"topicId":…,"correct":…,"answered":…}]`). */
    val perTopicJson: String,
    val seed: Long,
)
