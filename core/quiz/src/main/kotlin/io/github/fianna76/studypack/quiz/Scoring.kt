package io.github.fianna76.studypack.quiz

import kotlin.math.roundToInt

enum class Verdict {
    /** 85 % or more. */
    DEMO_READY,

    /** 65 % or more. */
    NEARLY_THERE,

    /** Below 65 %. */
    REVISE,
}

data class TopicScore(
    val topicId: String,
    val topicName: String,
    val correct: Int,
    val answered: Int,
) {
    val fraction: Float get() = if (answered == 0) 0f else correct.toFloat() / answered
}

/** The score of a finished (or ended early) session. Only answered questions count. */
data class QuizScore(
    val total: Int,
    val answered: Int,
    val correct: Int,
    /** Topics that had answered questions, worst first. */
    val perTopic: List<TopicScore>,
) {
    val percent: Int get() = Scoring.percent(correct, answered)

    val verdict: Verdict get() = Scoring.verdict(percent)
}

object Scoring {

    const val DEMO_READY_PERCENT = 85
    const val NEARLY_THERE_PERCENT = 65

    fun percent(correct: Int, answered: Int): Int = if (answered <= 0) 0 else (correct * 100.0 / answered).roundToInt()

    fun verdict(percent: Int): Verdict = when {
        percent >= DEMO_READY_PERCENT -> Verdict.DEMO_READY
        percent >= NEARLY_THERE_PERCENT -> Verdict.NEARLY_THERE
        else -> Verdict.REVISE
    }

    /**
     * Scores [questions] against [answers] (display index chosen for each question, `null` if
     * not answered). Topic names come from [topicNames], falling back to the topic ID.
     */
    fun score(questions: List<SessionQuestion>, answers: List<Int?>, topicNames: Map<String, String>): QuizScore {
        require(answers.size == questions.size) { "One answer slot per question" }
        val answered = questions.indices.filter { answers[it] != null }
        val correct = answered.count { answers[it] == questions[it].correctIndex }
        val perTopic = answered.groupBy { questions[it].question.topicId }.map { (topicId, indices) ->
            TopicScore(
                topicId = topicId,
                topicName = topicNames[topicId] ?: topicId,
                correct = indices.count { answers[it] == questions[it].correctIndex },
                answered = indices.size,
            )
        }.sortedWith(compareBy<TopicScore> { it.fraction }.thenByDescending { it.answered }.thenBy { it.topicName })
        return QuizScore(total = questions.size, answered = answered.size, correct = correct, perTopic = perTopic)
    }
}
