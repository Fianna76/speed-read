package io.github.fianna76.speedread.news.sports

/** Where a series' calendar comes from. */
sealed interface CalendarSource {
    /** The sportstimes project's JSON (`_db/<slug>/<year>.json`), also behind f1calendar.com. */
    data class SportsTimes(val slug: String) : CalendarSource {
        fun url(year: Int) = "https://raw.githubusercontent.com/sportstimes/f1/main/_db/$slug/$year.json"
    }

    /** A published iCalendar file. */
    data class Ics(val url: String) : CalendarSource

    /** A Le Mans series site: season pages list race pages, which carry session times (see `LeMansCalendar`). */
    data class LeMansSite(val baseUrl: String) : CalendarSource {
        fun seasonUrl(year: Int) = "$baseUrl/en/season/$year"

        fun raceUrl(path: String) = baseUrl + path
    }
}

/**
 * A news feed for a series. Feeds that cover several series (e.g. all sports-car racing)
 * name the series in [mentions]; only matching stories are kept.
 */
data class SeriesFeed(val url: String, val outlet: String, val mentions: Regex? = null) {
    fun matches(text: String): Boolean = mentions?.containsMatchIn(text) ?: true
}

/** A motorsport championship the Sports section can follow. */
data class MotorsportSeries(
    /** Stable identifier, stored in settings; never rename. */
    val id: String,
    val name: String,
    /** Used in calendar titles: "F2 · Monaco". */
    val shortName: String,
    val group: Group,
    val calendar: CalendarSource?,
    val news: List<SeriesFeed>,
    /** Typical race length, for calendar entries. */
    val raceMinutes: Int = 60,
) {
    enum class Group { FORMULA, ENDURANCE, TOURING }
}
