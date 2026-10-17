package io.github.fianna76.speedread.news

import java.net.URI
import java.util.Locale

/** Broad subjects the Newsstand can be filtered by. */
enum class NewsTopic { POLITICS, BUSINESS, SPORT, SCIENCE_TECH, HEALTH, CLIMATE, CULTURE }

/**
 * Infers [NewsTopic]s from a story's URL path ("/news/sport/…", "/business/2026/…") and its
 * feed categories. URL sections are the most reliable signal across outlets; categories add
 * coverage where feeds provide them. A story may have several topics, or none (general news).
 */
object TopicClassifier {

    fun topics(item: FeedItem): Set<NewsTopic> {
        val words = pathSegments(item.url) + item.categories.flatMap { words(it) }
        return KEYWORDS.filterValues { keywords -> words.any { it in keywords } }.keys
    }

    private fun pathSegments(url: String): List<String> {
        val path = runCatching { URI(url).path }.getOrNull() ?: return emptyList()
        // Only the leading sections, never the slug: "/news/business/budget-…" but not "…/sport-shop-closes".
        return path.split('/').filter { it.isNotEmpty() }.dropLast(1).take(MAX_SECTIONS).flatMap { segment ->
            listOf(segment.lowercase(Locale.ROOT)) + words(segment)
        }
    }

    private fun words(text: String): List<String> = text.lowercase(Locale.ROOT).split(NON_LETTERS).filter { it.isNotEmpty() }

    private const val MAX_SECTIONS = 3
    private val NON_LETTERS = Regex("[^\\p{L}0-9]+")

    private val KEYWORDS: Map<NewsTopic, Set<String>> = mapOf(
        NewsTopic.POLITICS to setOf(
            "politics", "political", "election", "elections", "government", "parliament", "dail", "oireachtas",
            "polaitiocht", "brexit", "congress", "white-house", "uspolitics", "us-politics",
        ),
        NewsTopic.BUSINESS to setOf(
            "business", "economy", "economics", "money", "markets", "finance", "companies", "personal-finance",
            "property", "careers", "gno", "agribusiness",
        ),
        NewsTopic.SPORT to setOf(
            "sport", "sports", "football", "soccer", "rugby", "gaa", "hurling", "cricket", "tennis", "golf",
            "olympics", "athletics", "boxing", "formula1", "f1", "motorsport", "nfl", "nba", "cycling", "racing",
            "grandstand", "spoirt",
        ),
        NewsTopic.SCIENCE_TECH to setOf(
            "science", "technology", "tech", "space", "innovation", "ai", "digital", "internet", "eolaiocht",
            "sci-tech", "science-technology",
        ),
        NewsTopic.HEALTH to setOf("health", "medicine", "wellbeing", "medical", "covid", "nhs", "hse", "slainte"),
        NewsTopic.CLIMATE to setOf(
            "climate", "environment", "climate-crisis", "climate-change", "biodiversity", "timpeallacht",
        ),
        NewsTopic.CULTURE to setOf(
            "culture", "arts", "entertainment", "music", "film", "films", "books", "lifestyle", "life-style", "tv",
            "television", "celebrity", "showbiz", "theatre", "food", "travel", "cultur", "ealain",
        ),
    )
}
