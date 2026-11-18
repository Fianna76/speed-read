package io.github.fianna76.studypack.quiz

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Builders for pack fixtures: a small valid pack, plus helpers to break it in one place. */
object TestPacks {

    fun question(
        id: String,
        topic: String,
        variantGroup: String? = null,
        tags: List<String> = emptyList(),
        correct: String = "Right answer for $id",
        distractors: List<String> = listOf("First wrong answer", "Second wrong option", "A third wrong answer that is the longest"),
    ): JsonObject = buildJsonObject {
        put("id", id)
        put("topic", topic)
        put("type", "single")
        put("difficulty", 2)
        put("prompt", "Prompt for $id?")
        put("correct", correct)
        putJsonArray("distractors") { distractors.forEach { add(it) } }
        put("explanation", "Because of reasons ($id).")
        put("modelAnswer", "Model answer for $id")
        put("sayIt", "Say it for $id")
        if (variantGroup != null) put("variantGroup", variantGroup) else put("variantGroup", JsonNull)
        put("source", "Lecture 1")
        putJsonArray("tags") { tags.forEach { add(it) } }
    }

    fun pack(
        topics: List<String> = listOf("alpha", "beta", "gamma"),
        questions: List<JsonObject> = topics.flatMap { t -> (1..4).map { question("$t-00$it", t) } },
        packId: String = "test-pack",
        version: String = "1.0.0",
        notes: List<String> = listOf("TEST101-test-overview.pdf"),
    ): JsonObject = buildJsonObject {
        put("format", "studypack-mcq")
        put("schemaVersion", 1)
        putJsonObject("pack") {
            put("id", packId)
            put("title", "Test Pack")
            put("moduleCode", "TEST101")
            put("moduleName", "Testing")
            put("version", version)
            put("created", "2026-11-18")
            put("description", "A pack for tests")
            putJsonArray("notes") { notes.forEach { add(it) } }
        }
        putJsonArray("topics") {
            topics.forEach { t ->
                add(buildJsonObject { put("id", t); put("name", t.replaceFirstChar(Char::uppercase)) })
            }
        }
        put("questions", JsonArray(questions))
    }

    fun JsonObject.with(key: String, value: JsonElement?): JsonObject =
        JsonObject(if (value == null) this - key else this + (key to value))

    fun JsonObject.withPack(key: String, value: JsonElement?): JsonObject =
        with("pack", (this["pack"] as JsonObject).with(key, value))

    /** Replaces field [key] of question [index]. */
    fun JsonObject.withQuestion(index: Int, key: String, value: JsonElement?): JsonObject {
        val questions = (this["questions"] as JsonArray).toMutableList()
        questions[index] = (questions[index] as JsonObject).with(key, value)
        return with("questions", JsonArray(questions))
    }

    fun strings(vararg values: String) = buildJsonArray { values.forEach { add(it) } }

    fun JsonObject.text(): String = Json.encodeToString(JsonObject.serializer(), this)

    fun str(value: String) = JsonPrimitive(value)

    fun fixture(name: String): ByteArray =
        checkNotNull(TestPacks::class.java.getResourceAsStream("/fixtures/$name")) { "Missing fixture $name" }.use { it.readBytes() }

    /** Plain questions for engine tests, [perTopic] per topic. */
    fun questions(topics: Int, perTopic: Int, packId: String = "p"): List<Question> =
        (0 until topics).flatMap { t ->
            (0 until perTopic).map { i -> engineQuestion("t$t-q$i", "t$t", packId = packId) }
        }

    fun engineQuestion(id: String, topic: String, variantGroup: String? = null, packId: String = "p", tags: List<String> = emptyList()) =
        Question(
            packId = packId,
            id = id,
            topicId = topic,
            difficulty = 2,
            prompt = "Prompt $id",
            correct = "correct $id",
            distractors = listOf("d1 $id", "d2 $id", "d3 $id"),
            explanation = "Explanation $id",
            variantGroup = variantGroup,
            tags = tags,
        )
}
