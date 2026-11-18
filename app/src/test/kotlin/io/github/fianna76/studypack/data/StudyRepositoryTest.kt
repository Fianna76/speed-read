package io.github.fianna76.studypack.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.studypack.quiz.PackParser
import io.github.fianna76.studypack.quiz.QuestionKey
import io.github.fianna76.studypack.quiz.QuizScore
import io.github.fianna76.studypack.quiz.StudyPack
import io.github.fianna76.studypack.quiz.TopicScore
import io.github.fianna76.studypack.testing.TestGraph
import io.github.fianna76.studypack.testing.TestPacks
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class StudyRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val graph by lazy { TestGraph(tmp.newFolder("app")) }
    private val repository get() = graph.repository

    @After
    fun tearDown() = graph.close()

    private fun pack(json: String = TestPacks.json()): StudyPack = checkNotNull(PackParser.parse(json).pack)

    private fun score(correct: Int, answered: Int) = QuizScore(total = answered, answered = answered, correct = correct, perTopic = listOf(TopicScore("a", "Topic A", correct, answered)))

    @Test
    fun savedPackComesBackInFileOrder() = runBlocking {
        repository.savePack(pack())
        val content = repository.quizContent(listOf("test-pack"))
        assertEquals(listOf("a-1", "a-2", "b-1", "b-2"), content.questions.map { it.id })
        assertEquals(mapOf("a" to "Topic A", "b" to "Topic B"), content.topicNames)
        val q = content.questions.first()
        assertEquals(listOf("Wrong one for a-1", "Wrong two for a-1", "Wrong three for a-1"), q.distractors)
        assertEquals("Say a-1", q.sayIt)
    }

    @Test
    fun answersFollowTheWeakSpotRule() = runBlocking {
        repository.savePack(pack())
        val key = QuestionKey("test-pack", "a-1")
        repository.recordAnswer(key, correct = false, tutorMode = true)
        repository.recordAnswer(key, correct = false, tutorMode = false)
        repository.recordAnswer(key, correct = true, tutorMode = false)
        assertEquals(2, repository.history(listOf("test-pack"))[key]?.wrong)
        repository.recordAnswer(key, correct = true, tutorMode = true)
        val history = repository.history(listOf("test-pack"))[key]!!
        assertEquals(4, history.seen)
        assertEquals(1, history.wrong)
        assertEquals(true, history.lastCorrect)
    }

    @Test
    fun reimportKeepsHistoryForUnchangedIds() = runBlocking {
        repository.savePack(pack())
        repository.recordAnswer(QuestionKey("test-pack", "a-1"), correct = false, tutorMode = true)
        repository.recordAnswer(QuestionKey("test-pack", "b-2"), correct = false, tutorMode = true)
        repository.savePack(pack(TestPacks.json(version = "1.1.0", questionIds = listOf("a-1", "b-3"))))
        val history = repository.history(listOf("test-pack"))
        assertEquals(1, history[QuestionKey("test-pack", "a-1")]?.wrong)
        // Stats for removed questions are left alone (harmless) until the pack is deleted.
        assertEquals(1, history[QuestionKey("test-pack", "b-2")]?.wrong)
        assertEquals(listOf("a-1", "b-3"), repository.quizContent(listOf("test-pack")).questions.map { it.id })
        assertEquals("1.1.0", repository.pack("test-pack")?.version)
    }

    @Test
    fun deletingAPackRemovesItsHistoryAndEmptyModule() = runBlocking {
        repository.savePack(pack())
        repository.recordAnswer(QuestionKey("test-pack", "a-1"), correct = false, tutorMode = true)
        repository.deletePack("test-pack")
        assertTrue(repository.history(listOf("test-pack")).isEmpty())
        assertNull(repository.pack("test-pack"))
        assertTrue(repository.observeModules().first().isEmpty())
        assertTrue(repository.modules().isEmpty())
    }

    @Test
    fun moduleWithNotesSurvivesPackDeletion() = runBlocking {
        repository.savePack(pack())
        val path = graph.files.newNotePath()
        graph.files.store(File(tmp.root, "n.pdf").apply { writeText("%PDF") }, path)
        val id = repository.saveNote("TEST101", null, "n.pdf", "Notes", path, 3, replacing = null)
        repository.deletePack("test-pack")
        val module = repository.observeModules().first().single()
        assertEquals(0, module.packCount)
        assertEquals(1, module.noteCount)
        repository.deleteNote(id)
        assertTrue(repository.observeModules().first().isEmpty())
        assertTrue(!graph.files.file(path).exists())
    }

    @Test
    fun scoresShowOnModulesAndPacks() = runBlocking {
        repository.savePack(pack())
        repository.savePack(pack(TestPacks.json(packId = "other-pack")))
        graph.now = 10
        repository.saveSession("TEST101", 0, "BENCH_DEMO", false, listOf("test-pack"), score(9, 10), seed = 1)
        graph.now = 20
        repository.saveSession("TEST101", 0, "WARM_UP", false, listOf("test-pack", "other-pack"), score(1, 2), seed = 2)
        assertEquals(50, repository.observeModules().first().single().lastPercent)
        val packs = repository.observePacks("TEST101").first().associateBy { it.id }
        assertEquals(90, packs.getValue("test-pack").bestPercent)
        assertEquals(50, packs.getValue("test-pack").lastPercent)
        assertEquals(50, packs.getValue("other-pack").bestPercent)
        assertEquals(4, packs.getValue("test-pack").questionCount)
    }

    @Test
    fun topicsMergeAcrossPacksInModuleOrder() = runBlocking {
        repository.savePack(pack())
        repository.savePack(pack(TestPacks.json(packId = "other-pack")))
        val content = repository.quizContent(listOf("test-pack", "other-pack"))
        assertEquals(listOf("a", "b"), content.topics.map { it.id })
        assertEquals(8, content.questions.size)
        assertEquals("test-pack", content.questions.first().packId)
    }
}
