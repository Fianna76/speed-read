package io.github.fianna76.studypack.quiz

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.LocalDate
import java.time.format.DateTimeParseException

enum class Severity { ERROR, WARNING, INFO }

/**
 * One problem found in a pack. The [message] says what is wrong and how to fix it; it already
 * names the question when there is one, so it can be shown as it is.
 */
data class PackIssue(
    val severity: Severity,
    val message: String,
    val questionId: String? = null,
    val field: String? = null,
)

/** The outcome of reading a pack file: the pack if there were no errors, and every issue found. */
data class PackParseResult(
    val pack: StudyPack?,
    val issues: List<PackIssue>,
) {
    val errors: List<PackIssue> get() = issues.filter { it.severity == Severity.ERROR }
    val warnings: List<PackIssue> get() = issues.filter { it.severity == Severity.WARNING }
    val infos: List<PackIssue> get() = issues.filter { it.severity == Severity.INFO }
}

/**
 * Reads and validates `.studypack.json` files (`format: "studypack-mcq"`, `schemaVersion: 1`).
 *
 * Unknown fields are ignored, so packs from newer generators still import. Missing or invalid
 * required fields are errors, and the pack is refused. Over-long text and other soft problems
 * are warnings: the pack is imported anyway.
 */
object PackParser {

    const val FORMAT = "studypack-mcq"
    const val SCHEMA_VERSION = 1

    /** Larger files are refused before parsing. */
    const val MAX_BYTES = 5 * 1024 * 1024

    const val MAX_TITLE = 80
    const val MAX_DESCRIPTION = 300
    const val MAX_TOPIC_NAME = 40
    const val MAX_PROMPT = 400
    const val MAX_CORRECT = 160
    const val MAX_EXPLANATION = 1200
    const val DISTRACTOR_COUNT = 3
    const val DEFAULT_DIFFICULTY = 2

    /** Packs with at least this many questions are checked for a guessable "longest answer" bias. */
    private const val LENGTH_BIAS_MIN_QUESTIONS = 10
    private const val LENGTH_BIAS_THRESHOLD = 0.4

    private val PACK_ID = Regex("^[a-z0-9-]{3,64}$")
    private val SLUG = Regex("^[a-z0-9][a-z0-9_-]*$")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        allowTrailingComma = false
    }

    /** True if [bytes] starts (after whitespace or a byte-order mark) like a JSON object. */
    fun looksLikeJson(bytes: ByteArray): Boolean {
        var i = 0
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) i = 3
        while (i < bytes.size && bytes[i].toInt().toChar().isWhitespace()) i++
        return i < bytes.size && bytes[i] == '{'.code.toByte()
    }

    fun parse(bytes: ByteArray): PackParseResult {
        if (bytes.size > MAX_BYTES) {
            return failure("The file is ${bytes.size / 1024 / 1024} MB; packs can be at most ${MAX_BYTES / 1024 / 1024} MB. Split it into smaller packs.")
        }
        return parse(bytes.toString(Charsets.UTF_8).removePrefix("﻿"))
    }

    fun parse(text: String): PackParseResult {
        val root: JsonElement = try {
            json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            return failure("This is not valid JSON: ${e.shortMessage()}")
        }
        if (root !is JsonObject) return failure("This is not a StudyPack file: the top level must be a JSON object.")

        val format = (root["format"] as? JsonPrimitive)?.contentOrNull
        if (format != FORMAT) {
            val found = if (format == null) "it is missing" else "found \"$format\""
            return failure("This is not a StudyPack question pack: \"format\" must be \"$FORMAT\" ($found).", field = "format")
        }
        val schemaVersion = (root["schemaVersion"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
        when {
            schemaVersion == null -> return failure("\"schemaVersion\" is missing or not a whole number. Set it to $SCHEMA_VERSION.", field = "schemaVersion")
            schemaVersion > SCHEMA_VERSION -> return failure(
                "This pack uses schema version $schemaVersion, but this app reads version $SCHEMA_VERSION. Update the app.",
                field = "schemaVersion",
            )
            schemaVersion < 1 -> return failure("\"schemaVersion\" must be $SCHEMA_VERSION (found $schemaVersion).", field = "schemaVersion")
        }

        val dto = try {
            json.decodeFromJsonElement(PackFileDto.serializer(), root)
        } catch (e: SerializationException) {
            return failure(typeErrorMessage(e, root))
        } catch (e: IllegalArgumentException) {
            return failure(typeErrorMessage(e, root))
        }
        return Validator(dto).run()
    }

    private fun failure(message: String, field: String? = null) =
        PackParseResult(null, listOf(PackIssue(Severity.ERROR, message, field = field)))

    /** Explains a wrong-type value, naming the question by its ID when it is inside one. */
    private fun typeErrorMessage(e: Exception, root: JsonObject): String {
        val questions = root["questions"] as? JsonArray
        questions?.forEachIndexed { index, element ->
            val failure = try {
                json.decodeFromJsonElement(QuestionDto.serializer(), element)
                null
            } catch (inner: SerializationException) {
                inner
            } catch (inner: IllegalArgumentException) {
                inner
            }
            if (failure != null) {
                val id = ((element as? JsonObject)?.get("id") as? JsonPrimitive)?.contentOrNull
                val name = if (id != null) "Question $id" else "Question #${index + 1}"
                return "$name has a value of the wrong type: ${failure.shortMessage()}"
            }
        }
        return "A value has the wrong type: ${e.shortMessage()}"
    }

    private fun Exception.shortMessage(): String =
        message.orEmpty().lineSequence().firstOrNull().orEmpty().replace(Regex("^Unexpected JSON token at offset \\d+: "), "").trim()

    /** Semantic checks over a decoded file. Collects every issue rather than stopping at the first. */
    private class Validator(private val dto: PackFileDto) {
        private val issues = ArrayList<PackIssue>()

        private fun error(message: String, questionId: String? = null, field: String? = null) {
            issues += PackIssue(Severity.ERROR, message, questionId, field)
        }

        private fun warning(message: String, questionId: String? = null, field: String? = null) {
            issues += PackIssue(Severity.WARNING, message, questionId, field)
        }

        private fun info(message: String, field: String? = null) {
            issues += PackIssue(Severity.INFO, message, field = field)
        }

        fun run(): PackParseResult {
            val info = packInfo()
            val topics = topics()
            val questions = questions(info?.id, topics.mapTo(HashSet()) { it.id })
            if (questions.isNotEmpty()) crossChecks(questions, topics)
            val hasErrors = issues.any { it.severity == Severity.ERROR }
            val pack = if (!hasErrors && info != null) StudyPack(info, topics, questions) else null
            return PackParseResult(pack, issues.toList())
        }

        private fun packInfo(): PackInfo? {
            val pack = dto.pack ?: run {
                error("The \"pack\" block is missing. Add it with id, title, moduleCode, version and created.", field = "pack")
                return null
            }
            val id = pack.id?.trim()
            when {
                id.isNullOrEmpty() -> error("pack.id is missing. Give the pack a lowercase ID such as \"ee5105-ecg-amp\".", field = "pack.id")
                !PACK_ID.matches(id) -> error(
                    "pack.id \"$id\" must be 3 to 64 lowercase letters, digits or hyphens (for example \"ee5105-ecg-amp\").",
                    field = "pack.id",
                )
            }
            val title = pack.title.clean()
            if (title == null) error("pack.title is missing. Add a short title for the pack.", field = "pack.title")
            else if (title.length > MAX_TITLE) warning("pack.title is ${title.length} characters; keep it to $MAX_TITLE.", field = "pack.title")

            val moduleCode = pack.moduleCode.clean()
            if (moduleCode == null) error("pack.moduleCode is missing. Add the module code, for example \"EE5105\".", field = "pack.moduleCode")

            val version = pack.version.clean()
            when {
                version == null -> error("pack.version is missing. Use a version such as \"1.0.0\".", field = "pack.version")
                PackVersion.parse(version) == null -> error(
                    "pack.version \"$version\" is not a version number. Use MAJOR.MINOR.PATCH, for example \"1.0.0\".",
                    field = "pack.version",
                )
            }
            val created = pack.created.clean()
            if (created == null) {
                error("pack.created is missing. Add the date the pack was made, for example \"2026-11-18\".", field = "pack.created")
            } else if (!isIsoDate(created)) {
                warning("pack.created \"$created\" is not an ISO date (YYYY-MM-DD).", field = "pack.created")
            }
            val description = pack.description.clean()
            if (description != null && description.length > MAX_DESCRIPTION) {
                warning("pack.description is ${description.length} characters; keep it to $MAX_DESCRIPTION.", field = "pack.description")
            }
            if (id == null || !PACK_ID.matches(id) || title == null || moduleCode == null || version == null || created == null) return null
            return PackInfo(
                id = id,
                title = title,
                moduleCode = moduleCode,
                moduleName = pack.moduleName.clean(),
                version = version,
                created = created,
                description = description,
                notes = pack.notes.orEmpty().mapNotNull { it.clean() }.distinct(),
            )
        }

        private fun topics(): List<Topic> {
            val list = dto.topics
            if (list.isNullOrEmpty()) {
                error("\"topics\" is missing or empty. Add at least one topic with an id and a name.", field = "topics")
                return emptyList()
            }
            val seen = HashSet<String>()
            return list.mapIndexedNotNull { i, topic ->
                val id = topic.id?.trim()
                val name = topic.name.clean()
                val label = id?.takeIf { it.isNotEmpty() }?.let { "Topic \"$it\"" } ?: "Topic #${i + 1}"
                when {
                    id.isNullOrEmpty() -> error("$label has no id. Give it a lowercase slug such as \"cmrr\".", field = "topics[].id")
                    !SLUG.matches(id) -> error("$label: the id must be lowercase letters, digits, hyphens or underscores.", field = "topics[].id")
                    !seen.add(id) -> error("$label appears more than once. Topic IDs must be unique.", field = "topics[].id")
                }
                if (name == null) error("$label has no name. Add a short name to show on its chip.", field = "topics[].name")
                else if (name.length > MAX_TOPIC_NAME) warning("$label's name is ${name.length} characters; keep it to $MAX_TOPIC_NAME.", field = "topics[].name")
                if (id.isNullOrEmpty() || name == null) null else Topic(id, name, topic.description.clean())
            }.distinctBy { it.id }
        }

        private fun questions(packId: String?, topicIds: Set<String>): List<Question> {
            val list = dto.questions
            if (list.isNullOrEmpty()) {
                error("\"questions\" is missing or empty. A pack needs at least one question.", field = "questions")
                return emptyList()
            }
            val ids = HashSet<String>()
            return list.mapIndexedNotNull { i, q -> question(i, q, packId.orEmpty(), topicIds, ids) }
        }

        private fun question(index: Int, q: QuestionDto, packId: String, topicIds: Set<String>, ids: MutableSet<String>): Question? {
            val id = q.id?.trim()?.ifEmpty { null }
            val name = if (id != null) "Question $id" else "Question #${index + 1}"
            val before = issues.count { it.severity == Severity.ERROR }
            fun err(message: String, field: String) = error("$name: $message", id, field)
            fun warn(message: String, field: String) = warning("$name: $message", id, field)

            if (id == null) err("the id is missing. Give every question a unique ID that stays the same between versions.", "questions[].id")
            else if (!ids.add(id)) err("the ID is used by more than one question. Question IDs must be unique.", "questions[].id")

            val topic = q.topic?.trim()?.ifEmpty { null }
            if (topic == null) err("the topic is missing. Set it to one of the topic IDs.", "questions[].topic")
            else if (topic !in topicIds) err("the topic \"$topic\" is not in \"topics\". Add it there or fix the spelling.", "questions[].topic")

            val type = q.type?.trim()
            if (type == null) err("the type is missing. Set it to \"single\".", "questions[].type")
            else if (type != "single") err("the type \"$type\" is not supported. Only \"single\" (one correct answer) is.", "questions[].type")

            val difficulty = q.difficulty ?: DEFAULT_DIFFICULTY
            if (difficulty !in 1..3) warn("difficulty $difficulty is outside 1 to 3; using $DEFAULT_DIFFICULTY.", "questions[].difficulty")

            val prompt = q.prompt.clean()
            if (prompt == null) err("the prompt is empty.", "questions[].prompt")
            else if (prompt.length > MAX_PROMPT) warn("the prompt is ${prompt.length} characters; keep it to $MAX_PROMPT.", "questions[].prompt")

            val correct = q.correct.clean()
            if (correct == null) err("the correct answer is empty.", "questions[].correct")
            else if (correct.length > MAX_CORRECT) warn("the correct answer is ${correct.length} characters; keep it to $MAX_CORRECT.", "questions[].correct")

            val distractors = q.distractors.orEmpty().map { it.trim() }
            when {
                q.distractors == null -> err("distractors are missing. Add exactly $DISTRACTOR_COUNT wrong answers.", "questions[].distractors")
                distractors.size != DISTRACTOR_COUNT ->
                    err("there must be exactly $DISTRACTOR_COUNT distractors (found ${distractors.size}).", "questions[].distractors")
                distractors.any { it.isEmpty() } -> err("a distractor is empty.", "questions[].distractors")
                distractors.map { it.lowercase() }.toSet().size != distractors.size ->
                    err("two distractors are the same. All $DISTRACTOR_COUNT must be different.", "questions[].distractors")
                correct != null && distractors.any { it.equals(correct, ignoreCase = true) } ->
                    err("a distractor is the same as the correct answer.", "questions[].distractors")
            }
            distractors.filter { it.length > MAX_CORRECT }.forEach {
                warn("a distractor is ${it.length} characters; keep answers to $MAX_CORRECT.", "questions[].distractors")
            }

            val explanation = q.explanation.clean()
            if (explanation == null) err("the explanation is empty. Explain why the answer is right.", "questions[].explanation")
            else if (explanation.length > MAX_EXPLANATION) {
                warn("the explanation is ${explanation.length} characters; keep it to $MAX_EXPLANATION.", "questions[].explanation")
            }

            val failed = issues.count { it.severity == Severity.ERROR } > before
            if (failed || id == null || topic == null || prompt == null || correct == null || explanation == null) return null
            return Question(
                packId = packId,
                id = id,
                topicId = topic,
                difficulty = difficulty.takeIf { it in 1..3 } ?: DEFAULT_DIFFICULTY,
                prompt = prompt,
                correct = correct,
                distractors = distractors,
                explanation = explanation,
                modelAnswer = q.modelAnswer.clean(),
                sayIt = q.sayIt.clean(),
                variantGroup = q.variantGroup.clean(),
                source = q.source.clean(),
                tags = q.tags.orEmpty().mapNotNull { it.clean() }.distinct(),
            )
        }

        private fun crossChecks(questions: List<Question>, topics: List<Topic>) {
            questions.groupBy { it.variantGroup }.forEach { (group, members) ->
                if (group != null && members.size == 1) {
                    warning(
                        "Variant group \"$group\" has only one question (${members.single().id}). Add more variants or remove the group.",
                        members.single().id,
                        "questions[].variantGroup",
                    )
                }
            }
            val used = questions.mapTo(HashSet()) { it.topicId }
            topics.filter { it.id !in used }.forEach { info("Topic \"${it.name}\" has no questions yet.", field = "topics") }

            if (questions.size >= LENGTH_BIAS_MIN_QUESTIONS) {
                val longest = questions.count { q -> q.distractors.all { q.correct.length > it.length } }
                val share = longest.toDouble() / questions.size
                if (share > LENGTH_BIAS_THRESHOLD) {
                    warning(
                        "The correct answer is the longest option in ${(share * 100).toInt()} % of questions, which makes them easy to guess. " +
                            "Balance the answer lengths.",
                        field = "questions[].correct",
                    )
                }
            }
        }

        private fun isIsoDate(value: String): Boolean = try {
            LocalDate.parse(value.take(10))
            true
        } catch (_: DateTimeParseException) {
            false
        }

        private fun String?.clean(): String? = this?.trim()?.ifEmpty { null }
    }
}
