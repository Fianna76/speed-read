package io.github.fianna76.speedread.news.sports

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** A calendar document could not be read. */
class CalendarFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

private val JSON = Json { ignoreUnknownKeys = true }

/**
 * The sportstimes calendars (f1calendar.com and siblings): `{"races": [{"name", "location",
 * "round", "slug", "sessions": {"fp1": "2026-03-06T01:30:00Z", ...}}]}`.
 */
object SportsTimesCalendar {

    fun parse(json: String, series: MotorsportSeries): List<SportsEvent> {
        val races = try {
            JSON.parseToJsonElement(json).jsonObject["races"]?.jsonArray ?: JsonArray(emptyList())
        } catch (e: Exception) {
            throw CalendarFormatException("Not a sportstimes calendar", e)
        }
        return races.flatMap { element ->
            val race = element.jsonObject
            val name = race.string("name") ?: return@flatMap emptyList()
            val location = race.string("location")
            val slug = race.string("slug") ?: name
            val sessions = race["sessions"] as? JsonObject ?: return@flatMap emptyList()
            sessions.mapNotNull { (key, value) ->
                val start = parseInstant(value.jsonPrimitive.contentOrNull) ?: return@mapNotNull null
                SportsEvent(
                    id = "${series.id}:$slug:$key:${start / 1000}",
                    title = "${series.shortName} · $name",
                    detail = sessionName(key),
                    startMillis = start,
                    durationMinutes = sessionMinutes(key, series),
                    venue = location,
                    follows = setOf(series.id),
                    kind = SportsEvent.Kind.MOTORSPORT,
                )
            }
        }
    }

    /** sportstimes session keys to readable names. */
    internal fun sessionName(key: String): String = when (key.lowercase()) {
        "fp1" -> "Practice 1"
        "fp2" -> "Practice 2"
        "fp3" -> "Practice 3"
        "practice" -> "Practice"
        "qualifying" -> "Qualifying"
        "sprintqualifying" -> "Sprint qualifying"
        "sprint" -> "Sprint"
        "feature" -> "Feature race"
        "gp" -> "Grand Prix"
        "race" -> "Race"
        "race1" -> "Race 1"
        "race2" -> "Race 2"
        "race3" -> "Race 3"
        else -> key.replace(Regex("([a-z])([A-Z0-9])"), "$1 $2").replaceFirstChar { it.uppercase() }
    }

    private fun sessionMinutes(key: String, series: MotorsportSeries): Int = when (key.lowercase()) {
        "gp", "race", "feature" -> series.raceMinutes
        "qualifying", "sprintqualifying" -> 60
        else -> 60
    }
}

/**
 * ESPN's public NBA schedules: a team's season (`.../basketball/nba/teams/{id}/schedule`) or a
 * day's scoreboard (`.../basketball/nba/scoreboard?dates=YYYYMMDD`). Both list `events[]` with
 * `date` (UTC) and `competitions[0]`: `competitors[]` (`homeAway`, `team.abbreviation`),
 * `venue.fullName` and `broadcasts` (scoreboard: `names[]`; team schedule: `media.shortName`).
 */
object EspnNbaSchedule {

    fun parse(json: String): List<SportsEvent> {
        val events = try {
            JSON.parseToJsonElement(json).jsonObject["events"]?.jsonArray ?: JsonArray(emptyList())
        } catch (e: Exception) {
            throw CalendarFormatException("Not an ESPN scoreboard", e)
        }
        return events.mapNotNull { element ->
            val event = element.jsonObject
            val id = event.string("id") ?: return@mapNotNull null
            val start = parseInstant(event.string("date")) ?: return@mapNotNull null
            val competition = event["competitions"]?.jsonArray?.firstOrNull()?.jsonObject ?: return@mapNotNull null
            val sides = competition["competitors"]?.jsonArray.orEmpty().map { it.jsonObject }
            val home = sides.firstOrNull { it.string("homeAway") == "home" }?.team() ?: return@mapNotNull null
            val away = sides.firstOrNull { it.string("homeAway") == "away" }?.team() ?: return@mapNotNull null
            val broadcasts = competition["broadcasts"]?.jsonArray.orEmpty()
                .flatMap { b ->
                    val broadcast = b.jsonObject
                    broadcast["names"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull } +
                        listOfNotNull((broadcast["media"] as? JsonObject)?.string("shortName"))
                }
                .distinct()
            val homeName = NbaTeams.byAbbreviation(home)?.nickname ?: home
            val awayName = NbaTeams.byAbbreviation(away)?.nickname ?: away
            SportsEvent(
                id = "nba:$id",
                title = "$awayName at $homeName",
                detail = broadcasts.takeIf { it.isNotEmpty() }?.joinToString(", "),
                startMillis = start,
                durationMinutes = NBA_GAME_MINUTES,
                venue = competition["venue"]?.jsonObject?.string("fullName"),
                follows = setOf(home, away),
                kind = SportsEvent.Kind.NBA,
            )
        }
    }

    private fun JsonObject.team(): String? = (this["team"] as? JsonObject)?.string("abbreviation")

    /** A game with breaks usually runs about two and a half hours. */
    const val NBA_GAME_MINUTES = 150
}

/**
 * Plain iCalendar (`.ics`) files: one [SportsEvent] per `VEVENT`. Handles UTC times
 * (`20261102T130000Z`), times with a `TZID`, floating times (read as UTC) and all-day dates;
 * folded lines and escaped text are unfolded.
 */
object IcsCalendar {

    fun parse(text: String, series: MotorsportSeries, titleFor: (summary: String) -> String = { it }): List<SportsEvent> {
        val lines = unfold(text)
        if (lines.none { it.startsWith("BEGIN:VCALENDAR") }) throw CalendarFormatException("Not an iCalendar file")
        val events = ArrayList<SportsEvent>()
        var fields: MutableMap<String, Pair<Map<String, String>, String>>? = null
        for (line in lines) {
            when {
                line == "BEGIN:VEVENT" -> fields = HashMap()
                line == "END:VEVENT" -> {
                    fields?.let { event(it, series, titleFor)?.let(events::add) }
                    fields = null
                }
                fields != null -> {
                    val colon = line.indexOf(':').takeIf { it > 0 } ?: continue
                    val head = line.substring(0, colon).split(';')
                    val params = head.drop(1).mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].uppercase() to it[1] } }.toMap()
                    fields[head[0].uppercase()] = params to line.substring(colon + 1)
                }
            }
        }
        return events
    }

    private fun event(fields: Map<String, Pair<Map<String, String>, String>>, series: MotorsportSeries, titleFor: (String) -> String): SportsEvent? {
        val (startParams, startValue) = fields["DTSTART"] ?: return null
        val start = parseIcsTime(startValue, startParams["TZID"]) ?: return null
        val end = fields["DTEND"]?.let { (params, value) -> parseIcsTime(value, params["TZID"]) }
        val summary = fields["SUMMARY"]?.second?.let(::unescape)?.trim().orEmpty()
        val uid = fields["UID"]?.second ?: "$summary@$start"
        val allDay = startValue.length == 8
        val minutes = end?.let { ((it - start) / 60_000).toInt() }?.takeIf { it in 1..(7 * 24 * 60) } ?: if (allDay) 24 * 60 else series.raceMinutes
        return SportsEvent(
            id = "${series.id}:ics:$uid",
            title = "${series.shortName} · ${titleFor(summary)}",
            detail = null,
            startMillis = start,
            durationMinutes = minutes,
            venue = fields["LOCATION"]?.second?.let(::unescape)?.takeIf { it.isNotBlank() },
            follows = setOf(series.id),
            kind = SportsEvent.Kind.MOTORSPORT,
            allDay = allDay,
        )
    }

    internal fun parseIcsTime(value: String, tzid: String?): Long? = try {
        when {
            value.length == 8 -> LocalDate.parse(value, BASIC_DATE).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            value.endsWith("Z") -> LocalDateTime.parse(value.dropLast(1), BASIC_DATE_TIME).toInstant(ZoneOffset.UTC).toEpochMilli()
            else -> {
                val zone = tzid?.let { runCatching { ZoneId.of(it.trim('"')) }.getOrNull() } ?: ZoneOffset.UTC
                LocalDateTime.parse(value, BASIC_DATE_TIME).atZone(zone).toInstant().toEpochMilli()
            }
        }
    } catch (_: DateTimeParseException) {
        null
    }

    private fun unfold(text: String): List<String> {
        val out = ArrayList<String>()
        for (raw in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && out.isNotEmpty()) {
                out[out.lastIndex] = out.last() + raw.substring(1)
            } else if (raw.isNotEmpty()) {
                out += raw
            }
        }
        return out
    }

    private fun unescape(text: String): String =
        text.replace("\\n", " ").replace("\\N", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    private val BASIC_DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val BASIC_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
}

/**
 * The Le Mans series sites (fiawec.com, europeanlemansseries.com, asianlemansseries.com) publish
 * each race page with a schema.org `SportsEvent` in JSON-LD: the weekend's `startDate`/`endDate`
 * and `location`, and a `subEvent` per session ("Race - 6 Hours of Monza") whose `startDate` has a
 * UTC offset. Sessions without a time yet are left out; if none has one, the weekend becomes a
 * single all-day event.
 */
object LeMansCalendar {

    /** Race page paths ("/en/race/6-hours-of-monza-2026") linked from a season page, in page order. */
    fun racePaths(seasonHtml: String): List<String> =
        RACE_LINK.findAll(seasonHtml).map { it.groupValues[1].trim() }.distinct().toList()

    /** The year at the end of a race slug ("…-2027"), if any. */
    fun yearOf(path: String): Int? = Regex("-(\\d{4})$").find(path)?.groupValues?.get(1)?.toIntOrNull()

    fun parseRace(html: String, series: MotorsportSeries): List<SportsEvent> {
        val event = org.jsoup.Jsoup.parse(html).select("script[type=application/ld+json]").asSequence()
            .mapNotNull { script -> runCatching { JSON.parseToJsonElement(script.data()) }.getOrNull() as? JsonObject }
            .firstOrNull { it.string("@type") == "SportsEvent" && it["subEvent"] != null }
            ?: throw CalendarFormatException("No race data on the page")
        val raceName = event.string("name")?.removePrefix(series.shortName)?.trim()?.replace(Regex("\\s\\d{4}$"), "").orEmpty()
        val location = (event["location"] as? JsonObject)?.let { it.string("name") ?: it.string("address") }
        val pageId = event.string("@id") ?: raceName
        val sessions = event["subEvent"]?.jsonArray.orEmpty().mapNotNull { element ->
            val session = element as? JsonObject ?: return@mapNotNull null
            val start = parseInstant(session.string("startDate")) ?: return@mapNotNull null
            val label = session.string("name")?.substringBefore(" - $raceName")?.trim()?.ifEmpty { null } ?: "Session"
            SportsEvent(
                id = "${series.id}:${session.string("@id") ?: "$pageId#$label"}",
                title = "${series.shortName} \u00b7 $raceName",
                detail = label,
                startMillis = start,
                durationMinutes = if (label.startsWith("Race")) series.raceMinutes else 60,
                venue = location,
                follows = setOf(series.id),
                kind = SportsEvent.Kind.MOTORSPORT,
            )
        }
        if (sessions.isNotEmpty()) return sessions
        val start = event.string("startDate")?.let { runCatching { OffsetDateTime.parse(it).toLocalDate() }.getOrNull() } ?: return emptyList()
        val end = event.string("endDate")?.let { runCatching { OffsetDateTime.parse(it).toLocalDate() }.getOrNull() } ?: start
        return listOf(
            SportsEvent(
                id = "${series.id}:$pageId",
                title = "${series.shortName} \u00b7 $raceName",
                detail = null,
                startMillis = start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                durationMinutes = ((end.toEpochDay() - start.toEpochDay() + 1) * 24 * 60).toInt(),
                venue = location,
                follows = setOf(series.id),
                kind = SportsEvent.Kind.MOTORSPORT,
                allDay = true,
            ),
        )
    }

    private val RACE_LINK = Regex("href=\"(/en/race/[a-z0-9-]+)\\s*\"")
}

internal fun JsonObject.string(key: String): String? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

internal fun JsonObject.int(key: String): Int? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull

/** ISO-8601 instants, with or without seconds ("2026-10-27T23:30Z"). */
internal fun parseInstant(text: String?): Long? {
    if (text.isNullOrBlank()) return null
    return runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(text.replace("Z", ":00Z"), DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
}

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
