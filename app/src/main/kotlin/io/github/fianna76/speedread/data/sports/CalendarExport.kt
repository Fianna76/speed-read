package io.github.fianna76.speedread.data.sports

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.provider.CalendarContract
import io.github.fianna76.speedread.news.sports.SportsEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A calendar on the device the user can add events to (a Google account's calendar syncs to Google Calendar). */
data class DeviceCalendar(val id: Long, val name: String, val account: String, val isGoogle: Boolean, val isPrimary: Boolean)

/** How "Add all to calendar" went. */
data class CalendarExportResult(val added: Int, val updated: Int, val unchanged: Int)

/**
 * Puts sports events in the user's calendar.
 * - [insertIntent] opens the calendar app with one event filled in (no permission needed).
 * - [addAll] writes events straight into a chosen calendar (needs the calendar permission). Each
 *   event carries a stable UID, so adding again updates moved events instead of duplicating.
 */
object CalendarExport {

    fun insertIntent(event: SportsEvent): Intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.startMillis)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endMillis)
        .putExtra(CalendarContract.Events.TITLE, title(event))
        .putExtra(CalendarContract.Events.EVENT_LOCATION, event.venue)
        .putExtra(CalendarContract.Events.DESCRIPTION, description(event))
        .putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE)

    /** Calendars the user can write to, Google ones and the primary first. */
    suspend fun writableCalendars(resolver: ContentResolver): List<DeviceCalendar> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.IS_PRIMARY,
        )
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND ${CalendarContract.Calendars.VISIBLE} = 1"
        val calendars = ArrayList<DeviceCalendar>()
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection, selection,
            arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()), null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                calendars += DeviceCalendar(
                    id = cursor.getLong(0),
                    name = cursor.getString(1).orEmpty(),
                    account = cursor.getString(2).orEmpty(),
                    isGoogle = cursor.getString(3) == GOOGLE_ACCOUNT_TYPE,
                    isPrimary = cursor.getInt(4) == 1,
                )
            }
        }
        calendars.sortedWith(compareByDescending<DeviceCalendar> { it.isGoogle }.thenByDescending { it.isPrimary }.thenBy { it.name })
    }

    /** Adds [events] to calendar [calendarId], updating ones added before whose time or details changed. */
    suspend fun addAll(resolver: ContentResolver, calendarId: Long, events: List<SportsEvent>): CalendarExportResult =
        withContext(Dispatchers.IO) {
            val existing = HashMap<String, Pair<Long, ContentValues>>()
            resolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events._ID, CalendarContract.Events.UID_2445, CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND, CalendarContract.Events.TITLE),
                "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.UID_2445} LIKE ? AND ${CalendarContract.Events.DELETED} = 0",
                arrayOf(calendarId.toString(), "$UID_PREFIX%"),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    existing[cursor.getString(1)] = cursor.getLong(0) to ContentValues().apply {
                        put(CalendarContract.Events.DTSTART, cursor.getLong(2))
                        put(CalendarContract.Events.DTEND, cursor.getLong(3))
                        put(CalendarContract.Events.TITLE, cursor.getString(4))
                    }
                }
            }
            var added = 0
            var updated = 0
            var unchanged = 0
            for (event in events) {
                val values = values(calendarId, event)
                val previous = existing[uid(event)]
                when {
                    previous == null -> {
                        resolver.insert(CalendarContract.Events.CONTENT_URI, values)
                        added++
                    }
                    previous.second.getAsLong(CalendarContract.Events.DTSTART) != event.startMillis ||
                        previous.second.getAsLong(CalendarContract.Events.DTEND) != event.endMillis ||
                        previous.second.getAsString(CalendarContract.Events.TITLE) != title(event) -> {
                        resolver.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, previous.first), values, null, null)
                        updated++
                    }
                    else -> unchanged++
                }
            }
            CalendarExportResult(added, updated, unchanged)
        }

    internal fun values(calendarId: Long, event: SportsEvent) = ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, calendarId)
        put(CalendarContract.Events.TITLE, title(event))
        put(CalendarContract.Events.DESCRIPTION, description(event))
        put(CalendarContract.Events.EVENT_LOCATION, event.venue)
        put(CalendarContract.Events.DTSTART, event.startMillis)
        put(CalendarContract.Events.DTEND, event.endMillis)
        put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
        put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE)
        put(CalendarContract.Events.UID_2445, uid(event))
    }

    /** "Formula 1 · Abu Dhabi – Qualifying", "Nets at Celtics". */
    internal fun title(event: SportsEvent): String = listOfNotNull(event.title, event.detail.takeIf { event.kind == SportsEvent.Kind.MOTORSPORT }).joinToString(" – ")

    private fun description(event: SportsEvent): String = listOfNotNull(
        event.detail.takeIf { event.kind == SportsEvent.Kind.NBA }?.let { "TV: $it" },
        "Added by SpeedRead",
    ).joinToString("\n")

    internal fun uid(event: SportsEvent) = UID_PREFIX + event.id

    private const val UID_PREFIX = "speedread-sports:"
    private const val GOOGLE_ACCOUNT_TYPE = "com.google"
}
