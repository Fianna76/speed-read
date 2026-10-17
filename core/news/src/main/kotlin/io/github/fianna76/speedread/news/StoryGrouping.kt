package io.github.fianna76.speedread.news

import java.text.Normalizer
import java.util.Locale

/** One story as headlined by one or more outlets. */
data class StoryGroup<T>(
    /** The highest-ranked version, shown on the card. */
    val lead: T,
    /** Other outlets' versions of the same story, in rank order. */
    val related: List<T>,
)

/**
 * Groups headlines about the same event ("Pope discharged from hospital…" and "Pope Leo
 * leaves hospital after cancer surgery") so a story reported by several outlets takes one
 * card.
 *
 * Two headlines match when they share at least [MIN_SHARED_WORDS] significant words that
 * cover at least [MIN_OVERLAP] of the shorter headline's significant words. Significant
 * words are 3+ letters, lowercased, accent-folded, not numbers and not stop words. A trailing plural
 * "s" is dropped. The thresholds are deliberately conservative: a shared name ("Trump",
 * "Ukraine") alone never groups two stories.
 */
object StoryGrouping {

    const val MIN_SHARED_WORDS = 3
    const val MIN_OVERLAP = 0.5

    /**
     * Groups [items], which must already be in display (rank) order; each group keeps the
     * position of its lead. Items of the same outlet are never grouped together.
     */
    fun <T> group(items: List<T>, title: (T) -> String, outlet: (T) -> String): List<StoryGroup<T>> {
        val words = items.map { significantWords(title(it)) }
        val leads = ArrayList<Int>()
        val members = HashMap<Int, MutableList<Int>>()
        for (i in items.indices) {
            val lead = leads.firstOrNull { l ->
                val group = listOf(l) + members[l].orEmpty()
                group.none { outlet(items[it]) == outlet(items[i]) } && isSameStory(words[l], words[i])
            }
            if (lead == null) leads += i else members.getOrPut(lead) { ArrayList() } += i
        }
        return leads.map { l -> StoryGroup(items[l], members[l].orEmpty().map { items[it] }) }
    }

    fun isSameStory(a: String, b: String): Boolean = isSameStory(significantWords(a), significantWords(b))

    private fun isSameStory(a: Set<String>, b: Set<String>): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        val shared = a.count { it in b }
        return shared >= MIN_SHARED_WORDS && shared >= MIN_OVERLAP * minOf(a.size, b.size)
    }

    internal fun significantWords(title: String): Set<String> =
        Normalizer.normalize(title.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .replace(POSSESSIVE, "")
            .split(NON_LETTERS)
            .asSequence()
            .filter { it.length >= 3 && it !in STOP_WORDS && !it.all(Char::isDigit) }
            .map { if (it.length > 4 && it.endsWith('s') && !it.endsWith("ss")) it.dropLast(1) else it }
            .toSet()

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val POSSESSIVE = Regex("['\u2019]s\\b")
    private val NON_LETTERS = Regex("[^\\p{L}\\p{N}]+")

    private val STOP_WORDS = setOf(
        "the", "and", "for", "with", "from", "into", "onto", "over", "under", "after", "before", "about", "amid",
        "against", "says", "said", "say", "will", "would", "could", "should", "can", "may", "might", "has", "have",
        "had", "was", "were", "are", "been", "being", "its", "his", "her", "their", "our", "your", "this", "that",
        "these", "those", "what", "when", "where", "who", "why", "how", "not", "but", "out", "off", "new", "more",
        "than", "then", "now", "just", "also", "all", "any", "some", "one", "two", "three", "first", "last", "year",
        "years", "day", "days", "week", "says", "via", "per", "how", "why", "live", "latest", "update", "updates",
        "news", "report", "reports", "watch", "video", "you", "they", "them", "him", "she", "its", "it's",
    )
}
