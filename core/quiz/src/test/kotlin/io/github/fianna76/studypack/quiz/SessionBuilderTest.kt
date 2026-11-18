package io.github.fianna76.studypack.quiz

import io.github.fianna76.studypack.quiz.TestPacks.engineQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SessionBuilderTest {

    private val ecg: StudyPack by lazy { PackParser.parse(TestPacks.fixture("EE5105-ecg-amp.studypack.json")).pack!! }

    @Test
    fun stratifiedPickCoversEveryTopicWhenLongEnough() {
        val pool = TestPacks.questions(topics = 10, perTopic = 8)
        repeat(1_000) { seed ->
            for (length in listOf(QuizLength.WARM_UP, QuizLength.BENCH_DEMO, QuizLength.FULL_MOCK)) {
                val session = SessionBuilder.build(pool, length, emptyMap(), Random(seed))
                assertEquals(length.questionCount, session.size)
                assertEquals("seed $seed $length", 10, session.map { it.question.topicId }.toSet().size)
                assertEquals(session.size, session.map { it.question.key }.toSet().size)
            }
        }
    }

    @Test
    fun ecgBenchDemoCoversAllTenTopics() {
        repeat(1_000) { seed ->
            val session = SessionBuilder.build(ecg.questions, QuizLength.BENCH_DEMO, emptyMap(), Random(seed))
            assertEquals(25, session.size)
            assertEquals(10, session.map { it.question.topicId }.toSet().size)
        }
    }

    @Test
    fun stratifiedPickIsBalancedAcrossTopics() {
        // 25 from 10 topics: every topic gets 2 or 3 questions.
        val pool = TestPacks.questions(topics = 10, perTopic = 8)
        val counts = SessionBuilder.build(pool, QuizLength.BENCH_DEMO, emptyMap(), Random(7)).groupingBy { it.question.topicId }.eachCount()
        assertTrue(counts.values.all { it in 2..3 })
    }

    @Test
    fun shortTopicsAreExhaustedWithoutStoppingOthers() {
        val pool = TestPacks.questions(topics = 1, perTopic = 30) + engineQuestion("lonely", "small")
        val session = SessionBuilder.build(pool, QuizLength.BENCH_DEMO, emptyMap(), Random(1))
        assertEquals(25, session.size)
        assertTrue(session.any { it.question.id == "lonely" })
    }

    @Test
    fun fixedLengthLargerThanPoolUsesWholePool() {
        val pool = TestPacks.questions(topics = 2, perTopic = 3)
        assertEquals(6, SessionBuilder.build(pool, QuizLength.FULL_MOCK, emptyMap(), Random(1)).size)
        assertEquals(6, SessionBuilder.sessionSize(pool, QuizLength.FULL_MOCK, emptyMap()))
    }

    @Test
    fun everythingShufflesWholePool() {
        val pool = TestPacks.questions(topics = 3, perTopic = 5)
        val session = SessionBuilder.build(pool, QuizLength.EVERYTHING, emptyMap(), Random(3))
        assertEquals(pool.map { it.key }.toSet(), session.map { it.question.key }.toSet())
        assertNotEquals(pool.map { it.key }, session.map { it.question.key })
    }

    @Test
    fun variantGroupsGiveOneSlotPerSession() {
        repeat(1_000) { seed ->
            for (length in QuizLength.entries.filter { it != QuizLength.WEAK_SPOTS }) {
                val session = SessionBuilder.build(ecg.questions, length, emptyMap(), Random(seed))
                val groups = session.mapNotNull { it.question.variantGroup }
                assertEquals("seed $seed $length: $groups", groups.size, groups.toSet().size)
            }
        }
        assertEquals(138, SessionBuilder.build(ecg.questions, QuizLength.EVERYTHING, emptyMap(), Random(0)).size)
    }

    @Test
    fun variantInstanceChangesBetweenRuns() {
        val chosen = (0 until 50).map { seed ->
            SessionBuilder.build(ecg.questions, QuizLength.EVERYTHING, emptyMap(), Random(seed))
                .single { it.question.variantGroup == "calc-r3-gain" }.question.id
        }.toSet()
        assertTrue("Only saw $chosen", chosen.size > 1)
    }

    @Test
    fun variantGroupsAreScopedToTheirPack() {
        val pool = listOf(
            engineQuestion("a", "t", variantGroup = "calc", packId = "one"),
            engineQuestion("b", "t", variantGroup = "calc", packId = "one"),
            engineQuestion("a", "t", variantGroup = "calc", packId = "two"),
        )
        assertEquals(2, SessionBuilder.slotCount(pool))
    }

    @Test
    fun correctIndexIsTrackedAfterShuffle() {
        repeat(1_000) { seed ->
            val session = SessionBuilder.build(ecg.questions, QuizLength.BENCH_DEMO, emptyMap(), Random(seed))
            session.forEach { q ->
                assertEquals(q.question.correct, q.options[q.correctIndex])
                assertEquals(q.question.answers.toSet(), q.options.toSet())
            }
        }
    }

    @Test
    fun correctAnswerLandsInEveryPosition() {
        val q = engineQuestion("x", "t")
        val positions = (0 until 200).map { SessionBuilder.shuffleOptions(q, Random(it)).correctIndex }.toSet()
        assertEquals(setOf(0, 1, 2, 3), positions)
    }

    @Test
    fun sameSeedReproducesSession() {
        repeat(200) { seed ->
            val a = SessionBuilder.build(ecg.questions, QuizLength.BENCH_DEMO, emptyMap(), Random(seed.toLong()))
            val b = SessionBuilder.build(ecg.questions, QuizLength.BENCH_DEMO, emptyMap(), Random(seed.toLong()))
            assertEquals(a, b)
        }
    }

    @Test
    fun consecutiveSessionsDiffer() {
        val a = SessionBuilder.build(ecg.questions, QuizLength.BENCH_DEMO, emptyMap(), Random(1))
        val b = SessionBuilder.build(ecg.questions, QuizLength.BENCH_DEMO, emptyMap(), Random(2))
        assertNotEquals(a.map { it.question.key }, b.map { it.question.key })
        val common = a.map { it.question.key }.intersect(b.map { it.question.key }.toSet())
        // Somewhere in the shared questions, the answer order differs too.
        assertTrue(common.any { key -> a.first { it.question.key == key }.optionOrder != b.first { it.question.key == key }.optionOrder })
    }

    @Test
    fun weakSpotsListOnlyMissedQuestionsWorstFirst() {
        val pool = TestPacks.questions(topics = 2, perTopic = 20)
        val history = mapOf(
            pool[0].key to QuestionHistory(seen = 4, wrong = 4), // 1.0
            pool[1].key to QuestionHistory(seen = 4, wrong = 2), // 0.5, wrong 2
            pool[2].key to QuestionHistory(seen = 2, wrong = 1), // 0.5, wrong 1
            pool[3].key to QuestionHistory(seen = 10, wrong = 1), // 0.1
            pool[4].key to QuestionHistory(seen = 5, wrong = 0), // not missed
        )
        assertTrue(SessionBuilder.hasWeakSpots(pool, history))
        val session = SessionBuilder.build(pool, QuizLength.WEAK_SPOTS, history, Random(1))
        assertEquals(setOf(pool[0].key, pool[1].key, pool[2].key, pool[3].key), session.map { it.question.key }.toSet())
        assertEquals(4, SessionBuilder.sessionSize(pool, QuizLength.WEAK_SPOTS, history))
    }

    @Test
    fun weakSpotsTakeTheTop25ByRatioThenWrong() {
        val pool = TestPacks.questions(topics = 1, perTopic = 40)
        // Question i has ratio (i + 1) / 40 and wrong = i + 1: the top 25 are the last 25.
        val history = pool.withIndex().associate { (i, q) -> q.key to QuestionHistory(seen = 40, wrong = i + 1) }
        val session = SessionBuilder.build(pool, QuizLength.WEAK_SPOTS, history, Random(5))
        assertEquals(pool.takeLast(25).map { it.key }.toSet(), session.map { it.question.key }.toSet())
        // Ties on ratio are broken by the number of misses.
        val tied = mapOf(pool[0].key to QuestionHistory(2, 1), pool[1].key to QuestionHistory(4, 2))
        val ordering = (1..24).map { pool[10 + it].key to QuestionHistory(1, 1) }.toMap() + tied
        val picked = SessionBuilder.build(pool, QuizLength.WEAK_SPOTS, ordering, Random(5)).map { it.question.key }
        assertTrue(pool[1].key in picked)
        assertFalse(pool[0].key in picked)
    }

    @Test
    fun weakSpotsRollUpVariantGroups() {
        val pool = listOf(
            engineQuestion("v1", "t", variantGroup = "calc"),
            engineQuestion("v2", "t", variantGroup = "calc"),
            engineQuestion("plain", "t"),
        )
        val history = mapOf(pool[0].key to QuestionHistory(seen = 1, wrong = 1))
        repeat(100) { seed ->
            val session = SessionBuilder.build(pool, QuizLength.WEAK_SPOTS, history, Random(seed))
            assertEquals(1, session.size)
            assertEquals("calc", session.single().question.variantGroup)
        }
    }

    @Test
    fun noWeakSpotsWithoutMisses() {
        val pool = TestPacks.questions(topics = 2, perTopic = 3)
        assertFalse(SessionBuilder.hasWeakSpots(pool, emptyMap()))
        assertFalse(SessionBuilder.hasWeakSpots(pool, mapOf(pool[0].key to QuestionHistory(seen = 3, wrong = 0))))
        assertTrue(SessionBuilder.build(pool, QuizLength.WEAK_SPOTS, emptyMap(), Random(1)).isEmpty())
    }

    @Test
    fun filterPoolByTopicAndDemoAsked() {
        val pool = listOf(
            engineQuestion("a", "t1", tags = listOf(Question.DEMO_ASKED_TAG)),
            engineQuestion("b", "t1"),
            engineQuestion("c", "t2", tags = listOf(Question.DEMO_ASKED_TAG)),
        )
        assertEquals(listOf("a", "b"), SessionBuilder.filterPool(pool, setOf("t1"), demoAskedOnly = false).map { it.id })
        assertEquals(listOf("a", "c"), SessionBuilder.filterPool(pool, null, demoAskedOnly = true).map { it.id })
        assertEquals(listOf("a"), SessionBuilder.filterPool(pool, setOf("t1"), demoAskedOnly = true).map { it.id })
    }

    @Test
    fun retryUsesExactlyTheGivenQuestions() {
        val pool = ecg.questions.filter { it.variantGroup == "calc-phase" }
        val session = SessionBuilder.buildRetry(pool, Random(9))
        assertEquals(pool.map { it.key }.toSet(), session.map { it.question.key }.toSet())
    }

    @Test
    fun questionKeyRoundTrips() {
        val key = QuestionKey("ee5105-ecg-amp", "cmrr/001")
        assertEquals(key, QuestionKey.decode(key.encode()))
        assertEquals(null, QuestionKey.decode("nokey"))
    }
}
