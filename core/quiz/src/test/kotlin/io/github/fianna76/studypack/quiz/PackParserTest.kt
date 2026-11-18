package io.github.fianna76.studypack.quiz

import io.github.fianna76.studypack.quiz.TestPacks.str
import io.github.fianna76.studypack.quiz.TestPacks.strings
import io.github.fianna76.studypack.quiz.TestPacks.text
import io.github.fianna76.studypack.quiz.TestPacks.with
import io.github.fianna76.studypack.quiz.TestPacks.withPack
import io.github.fianna76.studypack.quiz.TestPacks.withQuestion
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PackParserTest {

    private fun parse(pack: JsonObject) = PackParser.parse(pack.text())

    private fun assertRefused(result: PackParseResult, vararg fragments: String) {
        assertNull("Pack should be refused: ${result.issues}", result.pack)
        val messages = result.errors.joinToString("\n") { it.message }
        fragments.forEach { assertTrue("Expected \"$it\" in:\n$messages", messages.contains(it)) }
    }

    @Test
    fun validPackParses() {
        val result = parse(TestPacks.pack())
        val pack = checkNotNull(result.pack) { result.issues.toString() }
        assertEquals("test-pack", pack.info.id)
        assertEquals("TEST101", pack.info.moduleCode)
        assertEquals(listOf("TEST101-test-overview.pdf"), pack.info.notes)
        assertEquals(3, pack.topics.size)
        assertEquals(12, pack.questions.size)
        assertTrue(result.errors.isEmpty())
        assertTrue(pack.questions.all { it.packId == "test-pack" && it.distractors.size == 3 })
    }

    @Test
    fun realEcgPackImportsCleanly() {
        val result = PackParser.parse(TestPacks.fixture("EE5105-ecg-amp.studypack.json"))
        val pack = result.pack
        assertNotNull("Errors: ${result.errors}", pack)
        pack!!
        assertEquals("EE5105", pack.info.moduleCode)
        assertEquals(10, pack.topics.size)
        assertEquals(183, pack.questions.size)
        assertEquals(listOf("EE5105-ecg-amp-overview.pdf"), pack.info.notes)
        assertEquals(10, pack.questions.mapNotNull { it.variantGroup }.toSet().size)
        assertEquals(138, SessionBuilder.slotCount(pack.questions))
        assertTrue(pack.questions.any { it.isDemoAsked })
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val pack = TestPacks.pack().with("generator", str("skill 2.0")).withQuestion(0, "hint", str("think"))
        assertNotNull(parse(pack).pack)
    }

    @Test
    fun byteOrderMarkAndWhitespaceAreAccepted() {
        val bytes = "﻿  \n".toByteArray() + TestPacks.pack().text().toByteArray()
        assertTrue(PackParser.looksLikeJson(bytes))
        assertNotNull(PackParser.parse(bytes).pack)
        assertTrue(!PackParser.looksLikeJson("%PDF-1.7".toByteArray()))
    }

    @Test
    fun wrongFormatIsRefused() {
        assertRefused(parse(TestPacks.pack().with("format", str("quizlet"))), "\"format\" must be \"studypack-mcq\"", "found \"quizlet\"")
        assertRefused(parse(TestPacks.pack().with("format", null)), "it is missing")
    }

    @Test
    fun newerSchemaAsksForAnUpdate() {
        assertRefused(parse(TestPacks.pack().with("schemaVersion", JsonPrimitive(2))), "Update the app")
        assertRefused(parse(TestPacks.pack().with("schemaVersion", null)), "schemaVersion")
    }

    @Test
    fun invalidJsonIsRefused() {
        assertRefused(PackParser.parse("{ \"format\": "), "not valid JSON")
        assertRefused(PackParser.parse("[1, 2]"), "top level must be a JSON object")
    }

    @Test
    fun wrongTypeNamesTheQuestion() {
        val pack = TestPacks.pack().withQuestion(2, "difficulty", str("hard"))
        assertRefused(parse(pack), "Question alpha-003")
    }

    @Test
    fun missingPackFieldsAreErrors() {
        assertRefused(parse(TestPacks.pack().withPack("id", null)), "pack.id is missing")
        assertRefused(parse(TestPacks.pack().withPack("id", str("Bad ID!"))), "pack.id \"Bad ID!\"")
        assertRefused(parse(TestPacks.pack().withPack("moduleCode", null)), "pack.moduleCode is missing")
        assertRefused(parse(TestPacks.pack().withPack("version", str("one"))), "is not a version number")
        assertRefused(parse(TestPacks.pack().with("pack", null)), "\"pack\" block is missing")
    }

    @Test
    fun unknownTopicIsAnErrorNamingTheQuestion() {
        val result = parse(TestPacks.pack().withQuestion(0, "topic", str("delta")))
        assertRefused(result, "Question alpha-001", "topic \"delta\" is not in \"topics\"")
        assertEquals("alpha-001", result.errors.single().questionId)
        assertEquals("questions[].topic", result.errors.single().field)
    }

    @Test
    fun duplicateQuestionIdIsAnError() {
        val pack = TestPacks.pack().withQuestion(1, "id", str("alpha-001"))
        assertRefused(parse(pack), "Question alpha-001", "used by more than one question")
    }

    @Test
    fun distractorRules() {
        assertRefused(parse(TestPacks.pack().withQuestion(0, "distractors", strings("a", "b"))), "exactly 3 distractors (found 2)")
        assertRefused(parse(TestPacks.pack().withQuestion(0, "distractors", strings("a", "b", "c", "d"))), "found 4")
        assertRefused(parse(TestPacks.pack().withQuestion(0, "distractors", strings("a", "A", "c"))), "two distractors are the same")
        assertRefused(
            parse(TestPacks.pack().withQuestion(0, "distractors", strings("Right answer for alpha-001", "b", "c"))),
            "same as the correct answer",
        )
        assertRefused(parse(TestPacks.pack().withQuestion(0, "distractors", null)), "distractors are missing")
    }

    @Test
    fun emptyRequiredTextIsAnError() {
        assertRefused(parse(TestPacks.pack().withQuestion(0, "prompt", str("  "))), "the prompt is empty")
        assertRefused(parse(TestPacks.pack().withQuestion(0, "correct", null)), "the correct answer is empty")
        assertRefused(parse(TestPacks.pack().withQuestion(0, "explanation", str(""))), "the explanation is empty")
        assertRefused(parse(TestPacks.pack().withQuestion(0, "id", null)), "Question #1", "the id is missing")
    }

    @Test
    fun unsupportedTypeIsAnError() {
        assertRefused(parse(TestPacks.pack().withQuestion(0, "type", str("multi"))), "\"multi\" is not supported")
    }

    @Test
    fun overLongTextIsOnlyAWarning() {
        val result = parse(TestPacks.pack().withQuestion(0, "prompt", str("x".repeat(500))))
        assertNotNull(result.pack)
        val warning = result.warnings.single()
        assertTrue(warning.message.contains("Question alpha-001: the prompt is 500 characters"))
        assertEquals(500, result.pack!!.questions.first().prompt.length)
    }

    @Test
    fun loneVariantGroupIsAWarning() {
        val result = parse(TestPacks.pack().withQuestion(0, "variantGroup", str("calc-lonely")))
        assertNotNull(result.pack)
        assertTrue(result.warnings.single().message.contains("calc-lonely"))
    }

    @Test
    fun badDifficultyFallsBackToDefault() {
        val result = parse(TestPacks.pack().withQuestion(0, "difficulty", JsonPrimitive(7)))
        assertEquals(2, result.pack!!.questions.first().difficulty)
        assertEquals(1, result.warnings.size)
    }

    @Test
    fun allErrorsAreReportedAtOnce() {
        val pack = TestPacks.pack()
            .withQuestion(0, "topic", str("nope"))
            .withQuestion(1, "explanation", null)
            .withQuestion(2, "distractors", strings("a"))
        assertEquals(3, parse(pack).errors.size)
    }

    @Test
    fun guessableAnswerLengthsAreFlagged() {
        val questions = (1..10).map {
            TestPacks.question("alpha-$it", "alpha", correct = "A much longer correct answer than the others, number $it", distractors = listOf("a", "b", "c"))
        }
        val result = parse(TestPacks.pack(topics = listOf("alpha"), questions = questions))
        assertNotNull(result.pack)
        assertTrue(result.warnings.any { it.message.contains("longest option") })
    }

    @Test
    fun missingTopicsOrQuestionsAreErrors() {
        assertRefused(parse(TestPacks.pack().with("topics", JsonArray(emptyList()))), "\"topics\" is missing or empty")
        assertRefused(parse(TestPacks.pack().with("questions", null)), "\"questions\" is missing or empty")
    }

    @Test
    fun oversizedFileIsRefused() {
        val result = PackParser.parse(ByteArray(PackParser.MAX_BYTES + 1) { ' '.code.toByte() })
        assertRefused(result, "at most 5 MB")
    }
}
