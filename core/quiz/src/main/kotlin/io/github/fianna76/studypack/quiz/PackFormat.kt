package io.github.fianna76.studypack.quiz

import kotlinx.serialization.Serializable

// Wire format of `.studypack.json` (schemaVersion 1). Every field is optional here so that a
// missing field becomes a clear validation error naming the question, not a decoding failure.

@Serializable
internal data class PackFileDto(
    val format: String? = null,
    val schemaVersion: Int? = null,
    val pack: PackInfoDto? = null,
    val topics: List<TopicDto>? = null,
    val questions: List<QuestionDto>? = null,
)

@Serializable
internal data class PackInfoDto(
    val id: String? = null,
    val title: String? = null,
    val moduleCode: String? = null,
    val moduleName: String? = null,
    val version: String? = null,
    val created: String? = null,
    val description: String? = null,
    val notes: List<String>? = null,
)

@Serializable
internal data class TopicDto(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
)

@Serializable
internal data class QuestionDto(
    val id: String? = null,
    val topic: String? = null,
    val type: String? = null,
    val difficulty: Int? = null,
    val prompt: String? = null,
    val correct: String? = null,
    val distractors: List<String>? = null,
    val explanation: String? = null,
    val modelAnswer: String? = null,
    val sayIt: String? = null,
    val variantGroup: String? = null,
    val source: String? = null,
    val tags: List<String>? = null,
)
