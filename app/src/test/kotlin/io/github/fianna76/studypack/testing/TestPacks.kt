package io.github.fianna76.studypack.testing

import java.io.File

/** Small pack files for import tests. */
object TestPacks {

    fun json(
        packId: String = "test-pack",
        version: String = "1.0.0",
        moduleCode: String = "TEST101",
        notes: List<String> = listOf("TEST101-test-overview.pdf"),
        questionIds: List<String> = listOf("a-1", "a-2", "b-1", "b-2"),
        brokenTopicFor: String? = null,
    ): String {
        val questions = questionIds.joinToString(",\n") { id ->
            val topic = if (id == brokenTopicFor) "nope" else id.substringBefore('-')
            """
            {"id": "$id", "topic": "$topic", "type": "single", "prompt": "Question $id?",
             "correct": "Right $id", "distractors": ["Wrong one for $id", "Wrong two for $id", "Wrong three for $id"],
             "explanation": "Because $id.", "sayIt": "Say $id"}
            """.trimIndent()
        }
        val noteList = notes.joinToString(", ") { "\"$it\"" }
        return """
            {"format": "studypack-mcq", "schemaVersion": 1,
             "pack": {"id": "$packId", "title": "Pack $packId", "moduleCode": "$moduleCode", "moduleName": "Module $moduleCode",
                      "version": "$version", "created": "2026-11-18", "notes": [$noteList]},
             "topics": [{"id": "a", "name": "Topic A"}, {"id": "b", "name": "Topic B"}],
             "questions": [$questions]}
        """.trimIndent()
    }

    fun write(dir: File, name: String, content: String): File = File(dir, name).apply { writeText(content) }
}
