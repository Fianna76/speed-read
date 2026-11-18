package io.github.fianna76.studypack.quiz

import io.github.fianna76.studypack.quiz.TestPacks.engineQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringTest {

    private fun asked(id: String, topic: String) = SessionQuestion(engineQuestion(id, topic), listOf(2, 0, 3, 1)) // correct at B

    @Test
    fun verdictThresholds() {
        assertEquals(Verdict.DEMO_READY, Scoring.verdict(85))
        assertEquals(Verdict.NEARLY_THERE, Scoring.verdict(84))
        assertEquals(Verdict.NEARLY_THERE, Scoring.verdict(65))
        assertEquals(Verdict.REVISE, Scoring.verdict(64))
    }

    @Test
    fun scoresOnlyAnsweredQuestionsWithTopicsWorstFirst() {
        val questions = listOf(asked("1", "a"), asked("2", "a"), asked("3", "b"), asked("4", "b"), asked("5", "c"))
        val answers = listOf(1, 1, 0, 1, null)
        val score = Scoring.score(questions, answers, mapOf("a" to "Alpha", "b" to "Beta"))
        assertEquals(5, score.total)
        assertEquals(4, score.answered)
        assertEquals(3, score.correct)
        assertEquals(75, score.percent)
        assertEquals(Verdict.NEARLY_THERE, score.verdict)
        assertEquals(listOf("Beta", "Alpha"), score.perTopic.map { it.topicName })
        assertEquals(TopicScore("b", "Beta", correct = 1, answered = 2), score.perTopic.first())
    }

    @Test
    fun nothingAnsweredScoresZero() {
        val score = Scoring.score(listOf(asked("1", "a")), listOf(null), emptyMap())
        assertEquals(0, score.percent)
        assertTrue(score.perTopic.isEmpty())
    }

    @Test
    fun historyFollowsTheWebQuizRule() {
        val start = QuestionHistory()
        val missed = start.record(correct = false, tutorMode = true, now = 10)
        assertEquals(QuestionHistory(seen = 1, wrong = 1, lastSeenAt = 10, lastCorrect = false), missed)
        val madeUp = missed.record(correct = true, tutorMode = true, now = 20)
        assertEquals(0, madeUp.wrong)
        assertEquals(2, madeUp.seen)
        assertEquals(0, madeUp.record(correct = true, tutorMode = true, now = 30).wrong)
        // Exam mode never takes a miss away.
        assertEquals(1, missed.record(correct = true, tutorMode = false, now = 20).wrong)
    }

    @Test
    fun versionsCompareAsSemver() {
        fun v(s: String) = PackVersion.parse(s)!!
        assertTrue(v("1.0.1") > v("1.0.0"))
        assertTrue(v("1.10.0") > v("1.9.9"))
        assertTrue(v("2") > v("1.99.99"))
        assertEquals(0, v("1.2").compareTo(v("1.2.0")))
        assertTrue(v("1.0.0-beta") < v("1.0.0"))
        assertTrue(v("1.0.0-beta.2") < v("1.0.0-beta.10"))
        assertEquals(0, v("1.0.0+build5").compareTo(v("1.0.0")))
        assertNull(PackVersion.parse("one"))
        assertNull(PackVersion.parse("1.0.0.0"))
    }
}
