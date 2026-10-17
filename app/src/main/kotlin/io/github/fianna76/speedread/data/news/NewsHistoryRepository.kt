package io.github.fianna76.speedread.data.news

import io.github.fianna76.speedread.data.db.NewsHistoryDao
import io.github.fianna76.speedread.data.db.NewsHistoryEntity
import io.github.fianna76.speedread.news.FeedItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What the user has done with a story. */
data class StoryHistory(
    val item: FeedItem,
    val readAt: Long?,
    val savedAt: Long?,
) {
    val isRead: Boolean get() = readAt != null
    val isSaved: Boolean get() = savedAt != null
}

/**
 * Remembers stories the user opened (to mark them read) and saved (the reading list). Each
 * entry keeps the story's headline, so the reading list and "Continue reading" work after the
 * story has left the feed.
 */
class NewsHistoryRepository(
    private val dao: NewsHistoryDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** History by canonical URL. */
    fun observe(): Flow<Map<String, StoryHistory>> = dao.observeAll().map { entries ->
        entries.associate { it.url to StoryHistory(it.toFeedItem(), it.readAt, it.savedAt) }
    }

    suspend fun markRead(item: FeedItem) = setRead(item, read = true)

    suspend fun setRead(item: FeedItem, read: Boolean) {
        val now = clock()
        dao.update(item.canonicalUrl, initial = { item.toEntity() }) { entry ->
            entry.withHeadline(item).copy(readAt = if (read) now else null).takeIf { it.savedAt != null || it.readAt != null }
        }
    }

    suspend fun setSaved(item: FeedItem, saved: Boolean) {
        val now = clock()
        dao.update(item.canonicalUrl, initial = { item.toEntity() }) { entry ->
            val updated = entry.withHeadline(item).copy(savedAt = if (saved) entry.savedAt ?: now else null)
            // An unsaved story that was never read has nothing left worth keeping.
            updated.takeIf { it.savedAt != null || it.readAt != null }
        }
    }

    /** Forgets unsaved stories not opened within [ArticleRepository.RETENTION_MILLIS]. */
    suspend fun pruneOld() {
        dao.deleteUnsavedOlderThan(clock() - ArticleRepository.RETENTION_MILLIS)
    }

    private fun FeedItem.toEntity() = NewsHistoryEntity(
        url = canonicalUrl,
        link = url,
        sourceId = sourceId,
        title = title,
        summary = summary,
        imageUrl = imageUrl,
        publishedAt = publishedAt,
        author = author,
    )

    /** Refreshes the stored headline (outlets edit headlines) while keeping read/saved times. */
    private fun NewsHistoryEntity.withHeadline(item: FeedItem) =
        item.toEntity().copy(readAt = readAt, savedAt = savedAt, imageUrl = item.imageUrl ?: imageUrl)

    private fun NewsHistoryEntity.toFeedItem() = FeedItem(sourceId, title, link, summary, imageUrl, publishedAt, author)
}
