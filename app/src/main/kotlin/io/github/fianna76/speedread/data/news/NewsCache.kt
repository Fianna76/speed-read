package io.github.fianna76.speedread.data.news

import android.util.Log
import io.github.fianna76.speedread.news.FeedItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Stores the latest headlines as JSON so the Newsstand has something to show before (or without) a refresh. */
class NewsCache(private val file: File) {

    /** Blocking; call off the main thread. Unreadable or outdated caches read as empty. */
    fun read(): Map<String, SourceFeed> = try {
        if (!file.isFile) {
            emptyMap()
        } else {
            JSON.decodeFromString<CachedNews>(file.readText()).sources.associate { feed ->
                feed.sourceId to SourceFeed(feed.items.map { it.toFeedItem(feed.sourceId) }, feed.fetchedAt)
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Discarding unreadable news cache", e)
        emptyMap()
    }

    /** Blocking; best effort. */
    fun write(feeds: Map<String, SourceFeed>) {
        runCatching {
            val cached = CachedNews(
                version = VERSION,
                sources = feeds.map { (id, feed) -> CachedFeed(id, feed.fetchedAt, feed.items.map(CachedItem::from)) },
            )
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(JSON.encodeToString(cached))
            if (!temp.renameTo(file)) temp.delete()
        }.onFailure { Log.w(TAG, "Could not cache headlines", it) }
    }

    @Serializable
    private data class CachedNews(val version: Int, val sources: List<CachedFeed>)

    @Serializable
    private data class CachedFeed(val sourceId: String, val fetchedAt: Long, val items: List<CachedItem>)

    @Serializable
    private data class CachedItem(
        val title: String,
        val url: String,
        val summary: String,
        val imageUrl: String? = null,
        val publishedAt: Long? = null,
        val author: String? = null,
    ) {
        fun toFeedItem(sourceId: String) = FeedItem(sourceId, title, url, summary, imageUrl, publishedAt, author)

        companion object {
            fun from(item: FeedItem) = CachedItem(item.title, item.url, item.summary, item.imageUrl, item.publishedAt, item.author)
        }
    }

    private companion object {
        const val TAG = "NewsCache"
        const val VERSION = 1
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
