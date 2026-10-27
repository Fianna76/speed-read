package io.github.fianna76.speedread.news.sports

/** Something on the sports calendar: an NBA game or a motorsport session. */
data class SportsEvent(
    /** Stable across refreshes (used to avoid adding an event to a calendar twice). */
    val id: String,
    /** Main line: "Celtics vs Nets", "Formula 1 · Abu Dhabi". */
    val title: String,
    /** Second line: the session ("Qualifying", "Race"), or the game's broadcast. */
    val detail: String?,
    /** Start, epoch milliseconds (UTC). */
    val startMillis: Long,
    /** Expected length, for calendar entries. */
    val durationMinutes: Int,
    /** Venue or circuit. */
    val venue: String?,
    /** What it belongs to: an NBA team abbreviation per side, or the motorsport series id. */
    val follows: Set<String>,
    val kind: Kind,
    /** Only the day(s) are known (no session times yet): [startMillis] is midnight UTC of the first day. */
    val allDay: Boolean = false,
) {
    enum class Kind { NBA, MOTORSPORT }

    val endMillis: Long get() = startMillis + durationMinutes * 60_000L
}
