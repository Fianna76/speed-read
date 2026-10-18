package io.github.fianna76.speedread.data.news.background

import android.util.Log
import io.github.fianna76.speedread.news.AlertedStory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Remembers which stories were announced, so a story is alerted once. [isStarted] is false
 * until the first check after alerts are switched on: that check records what is already
 * leading the news instead of announcing it, so switching alerts on doesn't produce a burst
 * of old news.
 */
class NewsAlertLog(private val file: File, private val clock: () -> Long = System::currentTimeMillis) {

    /** [announcedAt] holds when each alert actually posted (others were only recorded), newest first. */
    data class Snapshot(val isStarted: Boolean, val alerted: List<AlertedStory>, val announcedAt: List<Long> = emptyList())

    /** Blocking; an unreadable log reads as not started. */
    fun read(): Snapshot = try {
        if (!file.isFile) {
            Snapshot(false, emptyList())
        } else {
            val log = JSON.decodeFromString<StoredLog>(file.readText())
            Snapshot(true, log.stories.map { AlertedStory(it.url, it.title) }, log.stories.filter { it.announced }.map { it.at })
        }
    } catch (e: Exception) {
        Log.w(TAG, "Discarding unreadable alert log", e)
        Snapshot(false, emptyList())
    }

    /**
     * Adds [stories] and starts the log; [announced] are the URLs an alert was posted for.
     * Entries older than [KEEP_MILLIS] are dropped. Blocking.
     */
    fun record(stories: List<AlertedStory>, announced: Set<String> = emptySet()) {
        val now = clock()
        val old = runCatching { JSON.decodeFromString<StoredLog>(file.readText()).stories }.getOrDefault(emptyList())
        val kept = (stories.map { StoredStory(it.url, it.title, now, announced = it.url in announced) } + old)
            .filter { now - it.at < KEEP_MILLIS }
            .distinctBy { it.url }
            .take(MAX_ENTRIES)
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(JSON.encodeToString(StoredLog(kept)))
            if (!temp.renameTo(file)) temp.delete()
        }.onFailure { Log.w(TAG, "Could not save alert log", it) }
    }

    /** Forgets everything, e.g. when alerts are switched off. Blocking. */
    fun clear() {
        file.delete()
    }

    @Serializable
    private data class StoredLog(val stories: List<StoredStory>)

    @Serializable
    private data class StoredStory(val url: String, val title: String, val at: Long, val announced: Boolean = false)

    private companion object {
        const val TAG = "NewsAlertLog"
        const val KEEP_MILLIS = 2 * 24 * 60 * 60 * 1000L
        const val MAX_ENTRIES = 200
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
