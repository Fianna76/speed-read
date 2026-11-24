package io.github.fianna76.studypack.quiz

import io.github.fianna76.studypack.quiz.TestPacks.engineQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** "Don't know": skipped questions are marked for revision, apart from wrong answers. */
class SkipTest {

    @Test
    fun skipMarksForRevisionWithoutCountingAsWrong() {
        val skipped = QuestionHistory().recordSkip(now = 5)
        assertEquals(QuestionHistory(seen = 1, wrong = 0, lastSeenAt = 5, lastCorrect = false, skipped = 1), skipped)
        assertTrue(skipped.isMarkedForRevision)
        assertEquals(2, skipped.recordSkip(6).skipped)
    }

    @Test
    fun aRightAnswerClearsTheMarkAWrongOneDoesNot() {
        val skipped = QuestionHistory().recordSkip(1).recordSkip(2)
        val wrong = skipped.record(correct = false, tutorMode = true, now = 3)
        assertTrue(wrong.isMarkedForRevision)
        assertEquals(1, wrong.wrong)
        for (tutor in listOf(true, false)) {
            assertFalse(skipped.record(correct = true, tutorMode = tutor, now = 3).isMarkedForRevision)
        }
    }

    @Test
    fun skipsAreNotWeakSpots() {
        val pool = TestPacks.questions(topics = 1, perTopic = 3)
        val history = mapOf(pool[0].key to QuestionHistory().recordSkip(1))
        assertFalse(SessionBuilder.hasWeakSpots(pool, history))
        assertTrue(SessionBuilder.hasMarkedForRevision(pool, history))
        assertTrue(SessionBuilder.build(pool, QuizLength.WEAK_SPOTS, history, Random(1)).isEmpty())
    }

    @Test
    fun revisionQuizAsksOnlyMarkedQuestionsMostSkippedFirst() {
        val pool = TestPacks.questions(topics = 2, perTopic = 30)
        val history = HashMap<QuestionKey, QuestionHistory>()
        // 50 marked questions: question i skipped (i % 3 + 1) times; plus wrong-only and cleared ones.
        pool.take(50).forEachIndexed { i, q -> history[q.key] = (0..i % 3).fold(QuestionHistory()) { h, _ -> h.recordSkip(i.toLong()) } }
        history[pool[50].key] = QuestionHistory().record(correct = false, tutorMode = true, now = 1)
        history[pool[51].key] = QuestionHistory().recordSkip(1).record(correct = true, tutorMode = false, now = 2)

        assertEquals(45, SessionBuilder.sessionSize(pool, QuizLength.MARKED_FOR_REVISION, history))
        val session = SessionBuilder.build(pool, QuizLength.MARKED_FOR_REVISION, history, Random(4)).map { it.question.key }
        assertEquals(45, session.size)
        assertTrue(session.all { history[it]!!.isMarkedForRevision })
        // Every question skipped three times made the cut (there are 17 of them).
        val thrice = pool.take(50).filterIndexed { i, _ -> i % 3 == 2 }.map { it.key }
        assertTrue(session.containsAll(thrice))
    }

    @Test
    fun revisionAsksTheVariantThatWasSkipped() {
        val pool = listOf(
            engineQuestion("v1", "t", variantGroup = "calc"),
            engineQuestion("v2", "t", variantGroup = "calc"),
            engineQuestion("v3", "t", variantGroup = "calc"),
        )
        val history = mapOf(pool[1].key to QuestionHistory().recordSkip(1))
        repeat(50) { seed ->
            assertEquals(listOf("v2"), SessionBuilder.build(pool, QuizLength.MARKED_FOR_REVISION, history, Random(seed)).map { it.question.id })
        }
    }

    @Test
    fun skippedAnswersScoreNothingButAreNotWrong() {
        val questions = (1..4).map { SessionQuestion(engineQuestion("q$it", "t"), listOf(0, 1, 2, 3)) } // correct at A
        val score = Scoring.score(questions, listOf(0, 1, Answers.SKIPPED, null), mapOf("t" to "Topic"))
        assertEquals(3, score.answered)
        assertEquals(1, score.correct)
        assertEquals(1, score.wrong)
        assertEquals(1, score.skipped)
        assertEquals(33, score.percent)
        assertEquals(TopicScore("t", "Topic", correct = 1, answered = 3), score.perTopic.single())
    }
}
