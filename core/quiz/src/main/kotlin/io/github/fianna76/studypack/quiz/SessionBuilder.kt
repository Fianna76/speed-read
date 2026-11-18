package io.github.fianna76.studypack.quiz

import kotlin.random.Random

/** Exam lengths offered on the setup screen. */
enum class QuizLength(val questionCount: Int?) {
    WARM_UP(12),
    BENCH_DEMO(25),
    FULL_MOCK(45),

    /** The whole (filtered) pool, shuffled. */
    EVERYTHING(null),

    /** Up to 25 of the most-missed questions. */
    WEAK_SPOTS(SessionBuilder.WEAK_SPOT_LIMIT),
}

/**
 * One question as asked in a session: [optionOrder] lists the answers in display order, as
 * indices into [Question.answers] (0 is the correct answer, 1 to 3 the distractors).
 */
data class SessionQuestion(
    val question: Question,
    val optionOrder: List<Int>,
) {
    init {
        require(optionOrder.sorted() == question.answers.indices.toList()) { "optionOrder must be a permutation of the answers" }
    }

    /** Answer texts in display order (A–D). */
    val options: List<String> get() = optionOrder.map { question.answers[it] }

    /** Display position of the correct answer. */
    val correctIndex: Int get() = optionOrder.indexOf(0)
}

/**
 * Builds quiz sessions, exactly like the web quiz:
 *
 * 1. The pool is filtered by topic (and optionally to `demo-asked` questions). Each variant group
 *    becomes one slot, filled by one instance chosen at random.
 * 2. Questions are chosen: fixed lengths use a stratified round-robin across topics so even short
 *    exams cover every topic; "everything" shuffles the whole pool; weak spots rank by
 *    `wrong / seen` (ties by `wrong`) and take the top 25.
 * 3. Each question's four answers are shuffled (Fisher–Yates), remembering where the correct one landed.
 *
 * All randomness comes from the given [Random], so a session is reproducible from its seed.
 */
object SessionBuilder {

    const val WEAK_SPOT_LIMIT = 25

    /** Questions in [questions] that match the topic filter (`null` = all topics) and the demo-asked filter. */
    fun filterPool(questions: List<Question>, topicIds: Set<String>?, demoAskedOnly: Boolean): List<Question> =
        questions.filter { (topicIds == null || it.topicId in topicIds) && (!demoAskedOnly || it.isDemoAsked) }

    /** Number of questions a session of [length] would have from [pool] (variant groups count once). */
    fun sessionSize(pool: List<Question>, length: QuizLength, history: Map<QuestionKey, QuestionHistory>): Int {
        val slots = slots(pool)
        return when (length) {
            QuizLength.EVERYTHING -> slots.size
            QuizLength.WEAK_SPOTS -> minOf(WEAK_SPOT_LIMIT, slots.count { slot -> slot.sumOf { history[it.key]?.wrong ?: 0 } > 0 })
            else -> minOf(length.questionCount ?: slots.size, slots.size)
        }
    }

    /** Number of slots (questions with variant groups collapsed) in [pool]. */
    fun slotCount(pool: List<Question>): Int = slots(pool).size

    /** True if any question in [pool] has been missed and not yet made up for. */
    fun hasWeakSpots(pool: List<Question>, history: Map<QuestionKey, QuestionHistory>): Boolean =
        pool.any { (history[it.key]?.wrong ?: 0) > 0 }

    fun build(
        pool: List<Question>,
        length: QuizLength,
        history: Map<QuestionKey, QuestionHistory>,
        random: Random,
    ): List<SessionQuestion> {
        val slots = slots(pool)
        val chosen = when (length) {
            QuizLength.EVERYTHING -> slots.map { it.random(random) }.shuffled(random)
            QuizLength.WEAK_SPOTS -> weakSpots(slots, history, random)
            else -> stratified(slots.map { it.random(random) }, length.questionCount ?: slots.size, random)
        }
        return chosen.map { shuffleOptions(it, random) }
    }

    /** A session over exactly [questions] (for "retry missed"): new order, new answer order, no variant swapping. */
    fun buildRetry(questions: List<Question>, random: Random): List<SessionQuestion> =
        questions.distinctBy { it.key }.shuffled(random).map { shuffleOptions(it, random) }

    fun shuffleOptions(question: Question, random: Random): SessionQuestion =
        SessionQuestion(question, question.answers.indices.toList().shuffled(random))

    /**
     * Groups the pool into slots, in pool order: a question without a variant group is a slot
     * of its own, and all instances of a group (within a pack) share one slot.
     */
    internal fun slots(pool: List<Question>): List<List<Question>> {
        val slots = LinkedHashMap<Any, MutableList<Question>>()
        for (question in pool) {
            val key: Any = question.variantGroup?.let { question.packId to it } ?: question.key
            slots.getOrPut(key) { ArrayList(1) } += question
        }
        return slots.values.toList()
    }

    /**
     * Stratified round-robin: group by topic, shuffle each group and the topic order, then take
     * one question per topic in turn until [count], and shuffle the result.
     */
    private fun stratified(questions: List<Question>, count: Int, random: Random): List<Question> {
        val byTopic = questions.groupBy { it.topicId }.values.map { it.shuffled(random).toMutableList() }.shuffled(random)
        val picked = ArrayList<Question>(minOf(count, questions.size))
        while (picked.size < count && byTopic.any { it.isNotEmpty() }) {
            for (group in byTopic) {
                if (picked.size >= count) break
                if (group.isNotEmpty()) picked += group.removeAt(0)
            }
        }
        return picked.shuffled(random)
    }

    /** Missed slots ranked worst first, by `wrong / seen` then `wrong`, rolled up across variant instances. */
    private fun weakSpots(slots: List<List<Question>>, history: Map<QuestionKey, QuestionHistory>, random: Random): List<Question> {
        data class Ranked(val slot: List<Question>, val wrong: Int, val ratio: Double)
        return slots.mapNotNull { slot ->
            val wrong = slot.sumOf { history[it.key]?.wrong ?: 0 }
            val seen = slot.sumOf { history[it.key]?.seen ?: 0 }
            if (wrong <= 0) null else Ranked(slot, wrong, wrong.toDouble() / seen.coerceAtLeast(wrong))
        }
            .sortedWith(compareByDescending<Ranked> { it.ratio }.thenByDescending { it.wrong })
            .take(WEAK_SPOT_LIMIT)
            .map { it.slot.random(random) }
            .shuffled(random)
    }
}
