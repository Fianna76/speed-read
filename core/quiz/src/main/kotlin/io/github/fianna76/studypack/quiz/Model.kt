package io.github.fianna76.studypack.quiz

/** A validated question pack, ready to be stored. */
data class StudyPack(
    val info: PackInfo,
    val topics: List<Topic>,
    val questions: List<Question>,
)

/** The `pack` block of a `.studypack.json` file. */
data class PackInfo(
    val id: String,
    val title: String,
    val moduleCode: String,
    val moduleName: String?,
    val version: String,
    val created: String,
    val description: String?,
    /** File names of the companion notes PDFs. */
    val notes: List<String>,
)

data class Topic(
    val id: String,
    val name: String,
    val description: String? = null,
)

/** Identifies a question across packs. Question IDs are only unique within their pack. */
data class QuestionKey(val packId: String, val questionId: String) {

    /** `packId/questionId`, for saved state and navigation arguments. */
    fun encode(): String = "$packId/$questionId"

    override fun toString(): String = encode()

    companion object {
        /** Parses [encode]'s output; pack IDs never contain a slash, question IDs may. */
        fun decode(value: String): QuestionKey? {
            val slash = value.indexOf('/')
            if (slash <= 0 || slash == value.lastIndex) return null
            return QuestionKey(value.substring(0, slash), value.substring(slash + 1))
        }
    }
}

/** A single-answer multiple-choice question: one correct answer and exactly three distractors. */
data class Question(
    val packId: String,
    val id: String,
    val topicId: String,
    val difficulty: Int,
    val prompt: String,
    val correct: String,
    val distractors: List<String>,
    val explanation: String,
    val modelAnswer: String? = null,
    val sayIt: String? = null,
    /** Questions sharing a group are pre-computed variants of one calculation; one is used per session. */
    val variantGroup: String? = null,
    val source: String? = null,
    val tags: List<String> = emptyList(),
) {
    val key: QuestionKey get() = QuestionKey(packId, id)

    val isCalculation: Boolean get() = variantGroup != null

    val isDemoAsked: Boolean get() = DEMO_ASKED_TAG in tags

    /** The four answer texts in pack order: the correct answer first, then the distractors. */
    val answers: List<String> get() = listOf(correct) + distractors

    companion object {
        /** Tag for questions asked in a real demo or viva; it gets its own filter chip. */
        const val DEMO_ASKED_TAG = "demo-asked"
    }
}

/** What the device remembers about one question. */
data class QuestionHistory(
    val seen: Int = 0,
    val wrong: Int = 0,
    val lastSeenAt: Long? = null,
    val lastCorrect: Boolean? = null,
) {
    /**
     * Records an answer, using the same weak-spot rule as the web quiz: a wrong answer adds 1
     * to [wrong]; in tutor mode a right answer takes 1 off (never below 0); [seen] always goes up.
     */
    fun record(correct: Boolean, tutorMode: Boolean, now: Long): QuestionHistory = copy(
        seen = seen + 1,
        wrong = when {
            !correct -> wrong + 1
            tutorMode -> (wrong - 1).coerceAtLeast(0)
            else -> wrong
        },
        lastSeenAt = now,
        lastCorrect = correct,
    )
}
