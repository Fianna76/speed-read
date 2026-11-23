package io.github.fianna76.studypack.data

import io.github.fianna76.studypack.data.db.ModuleEntity
import io.github.fianna76.studypack.data.db.NoteFileEntity
import io.github.fianna76.studypack.data.db.PackEntity
import io.github.fianna76.studypack.data.db.QuestionEntity
import io.github.fianna76.studypack.data.db.QuestionStatEntity
import io.github.fianna76.studypack.data.db.SessionResultEntity
import io.github.fianna76.studypack.data.db.StudyPackDatabase
import io.github.fianna76.studypack.data.db.TopicEntity
import io.github.fianna76.studypack.data.files.NoteFileStore
import io.github.fianna76.studypack.quiz.Question
import io.github.fianna76.studypack.quiz.QuestionHistory
import io.github.fianna76.studypack.quiz.QuestionKey
import io.github.fianna76.studypack.quiz.QuizScore
import io.github.fianna76.studypack.quiz.Scoring
import io.github.fianna76.studypack.quiz.StudyPack
import io.github.fianna76.studypack.quiz.Topic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

data class ModuleSummary(
    val code: String,
    val name: String?,
    val packCount: Int,
    val noteCount: Int,
    val questionCount: Int,
    val lastPercent: Int?,
)

data class PackSummary(
    val id: String,
    val title: String,
    val version: String,
    val description: String?,
    val questionCount: Int,
    val bestPercent: Int?,
    val lastPercent: Int?,
)

data class NoteFile(
    val id: Long,
    val moduleCode: String,
    val fileName: String,
    val displayTitle: String,
    val internalPath: String,
    val pageCount: Int,
    val lastPage: Int,
    /** Speed reading: the word reached in the whole document, or -1. */
    val rsvpWord: Int = -1,
)

/** Everything a quiz needs from a set of packs: topics (merged by ID across packs) and questions in a stable order. */
data class QuizContent(
    val topics: List<Topic>,
    val questions: List<Question>,
) {
    val topicNames: Map<String, String> get() = topics.associate { it.id to it.name }
}

@Serializable
private data class TopicScoreRecord(val topicId: String, val correct: Int, val answered: Int)

/** The single source of truth for packs, notes, history and session results. */
class StudyRepository(
    database: StudyPackDatabase,
    private val noteFiles: NoteFileStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val library = database.libraryDao()
    private val stats = database.statsDao()
    private val topicScores = ListSerializer(TopicScoreRecord.serializer())

    fun observeModules(): Flow<List<ModuleSummary>> = library.observeModuleSummaries().map { rows ->
        rows.map {
            ModuleSummary(
                code = it.code,
                name = it.name,
                packCount = it.packCount,
                noteCount = it.noteCount,
                questionCount = it.questionCount,
                lastPercent = it.lastAnswered?.let { answered -> Scoring.percent(it.lastCorrect ?: 0, answered) },
            )
        }
    }

    fun observeModule(code: String): Flow<ModuleEntity?> = library.observeModule(code)

    fun observePacks(moduleCode: String): Flow<List<PackSummary>> =
        combine(library.observePacks(moduleCode), stats.observeSessions(moduleCode)) { packs, sessions ->
            packs.map { pack ->
                val scored = sessions.filter { pack.id in it.packIds && it.answered > 0 }
                PackSummary(
                    id = pack.id,
                    title = pack.title,
                    version = pack.version,
                    description = pack.description,
                    questionCount = pack.questionCount,
                    bestPercent = scored.maxOfOrNull { Scoring.percent(it.correct, it.answered) },
                    lastPercent = scored.firstOrNull()?.let { Scoring.percent(it.correct, it.answered) },
                )
            }
        }

    fun observeNotes(moduleCode: String): Flow<List<NoteFile>> = library.observeNotes(moduleCode).map { list -> list.map { it.toNoteFile() } }

    fun observeNote(id: Long): Flow<NoteFile?> = library.observeNote(id).map { it?.toNoteFile() }

    suspend fun note(id: Long): NoteFile? = library.note(id)?.toNoteFile()

    suspend fun updateLastPage(noteId: Long, page: Int) = library.updateLastPage(noteId, page)

    /** Saves where speed reading got to: the page (for the viewer too) and the exact word. */
    suspend fun updateReadingPosition(noteId: Long, page: Int, word: Int) = library.updateReadingPosition(noteId, page, word)

    suspend fun packIdsInModule(moduleCode: String): List<String> = library.packsInModule(moduleCode).map { it.id }

    suspend fun quizContent(packIds: Collection<String>): QuizContent {
        val topics = library.topics(packIds)
            .sortedWith(compareBy({ packIds.indexOf(it.packId) }, { it.ordinal }))
            .distinctBy { it.id }
            .map { Topic(it.id, it.name, it.description) }
        val questions = library.questions(packIds)
            .sortedWith(compareBy({ packIds.indexOf(it.packId) }, { it.ordinal }))
            .map { it.toQuestion() }
        return QuizContent(topics, questions)
    }

    suspend fun history(packIds: Collection<String>): Map<QuestionKey, QuestionHistory> =
        stats.stats(packIds).associate { it.key() to it.toHistory() }

    fun observeHistory(packIds: Collection<String>): Flow<Map<QuestionKey, QuestionHistory>> =
        stats.observeStats(packIds).map { list -> list.associate { it.key() to it.toHistory() } }

    /** Updates a question's history with one answer (see [QuestionHistory.record]). */
    suspend fun recordAnswer(key: QuestionKey, correct: Boolean, tutorMode: Boolean) {
        val current = stats.stat(key.packId, key.questionId)?.toHistory() ?: QuestionHistory()
        val updated = current.record(correct, tutorMode, clock())
        stats.upsertStat(
            QuestionStatEntity(key.packId, key.questionId, updated.seen, updated.wrong, updated.lastSeenAt, updated.lastCorrect),
        )
    }

    suspend fun saveSession(
        moduleCode: String,
        startedAt: Long,
        mode: String,
        examMode: Boolean,
        packIds: List<String>,
        score: QuizScore,
        seed: Long,
    ): Long = stats.insertSession(
        SessionResultEntity(
            moduleCode = moduleCode,
            startedAt = startedAt,
            endedAt = clock(),
            mode = mode,
            examMode = examMode,
            packIds = packIds,
            total = score.total,
            answered = score.answered,
            correct = score.correct,
            perTopicJson = Json.encodeToString(topicScores, score.perTopic.map { TopicScoreRecord(it.topicId, it.correct, it.answered) }),
            seed = seed,
        ),
    )

    /** Deletes a pack with its questions, answer history and the scores that were only about it. */
    suspend fun deletePack(packId: String) = library.deletePack(packId)

    /** Deletes a module and everything in it: packs, history, scores, and the notes PDFs' files. */
    suspend fun deleteModule(moduleCode: String) {
        library.deleteModule(moduleCode).forEach(noteFiles::delete)
    }

    suspend fun deleteNote(noteId: Long) {
        val note = library.note(noteId) ?: return
        library.deleteNote(noteId)
        noteFiles.delete(note.internalPath)
    }

    // Import support

    suspend fun modules(): List<ModuleEntity> = library.modules()

    suspend fun pack(id: String): PackEntity? = library.pack(id)

    suspend fun allPacks(): List<PackEntity> = library.allPacks()

    suspend fun questionIds(packId: String): List<String> = library.questionIds(packId)

    suspend fun noteFileNames(moduleCode: String): List<String> = library.noteFileNames(moduleCode)

    suspend fun noteByName(moduleCode: String, fileName: String): NoteFile? = library.noteByName(moduleCode, fileName)?.toNoteFile()

    suspend fun savePack(pack: StudyPack) {
        val info = pack.info
        library.replacePack(
            moduleName = info.moduleName,
            pack = PackEntity(
                id = info.id,
                moduleCode = info.moduleCode,
                title = info.title,
                version = info.version,
                created = info.created,
                description = info.description,
                notes = info.notes,
                importedAt = clock(),
            ),
            topics = pack.topics.mapIndexed { i, t -> TopicEntity(info.id, t.id, t.name, t.description, i) },
            questions = pack.questions.mapIndexed { i, q ->
                QuestionEntity(
                    packId = info.id,
                    id = q.id,
                    ordinal = i,
                    topicId = q.topicId,
                    type = "single",
                    difficulty = q.difficulty,
                    prompt = q.prompt,
                    correct = q.correct,
                    distractors = q.distractors,
                    explanation = q.explanation,
                    modelAnswer = q.modelAnswer,
                    sayIt = q.sayIt,
                    variantGroup = q.variantGroup,
                    source = q.source,
                    tags = q.tags,
                )
            },
        )
    }

    /** Inserts a note, or replaces note [replacing] (same module and file name), keeping its ID. */
    suspend fun saveNote(
        moduleCode: String,
        moduleName: String?,
        fileName: String,
        displayTitle: String,
        internalPath: String,
        pageCount: Int,
        replacing: NoteFile?,
    ): Long = library.saveNote(
        moduleName,
        NoteFileEntity(
            id = replacing?.id ?: 0,
            moduleCode = moduleCode,
            fileName = fileName,
            displayTitle = displayTitle,
            internalPath = internalPath,
            pageCount = pageCount,
            lastPage = replacing?.lastPage?.coerceIn(0, (pageCount - 1).coerceAtLeast(0)) ?: 0,
            importedAt = clock(),
            // A replaced PDF has different text, so speed reading starts over.
            rsvpWord = -1,
        ),
    )

    private fun NoteFileEntity.toNoteFile() = NoteFile(id, moduleCode, fileName, displayTitle, internalPath, pageCount, lastPage, rsvpWord)

    private fun QuestionStatEntity.key() = QuestionKey(packId, questionId)

    private fun QuestionStatEntity.toHistory() = QuestionHistory(seen, wrong, lastSeenAt, lastCorrect)

    private fun QuestionEntity.toQuestion() = Question(
        packId = packId,
        id = id,
        topicId = topicId,
        difficulty = difficulty,
        prompt = prompt,
        correct = correct,
        distractors = distractors,
        explanation = explanation,
        modelAnswer = modelAnswer,
        sayIt = sayIt,
        variantGroup = variantGroup,
        source = source,
        tags = tags,
    )
}
