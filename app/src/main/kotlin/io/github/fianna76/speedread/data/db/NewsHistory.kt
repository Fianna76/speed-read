package io.github.fianna76.speedread.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A news story the user read or saved, with enough of its headline to show it after it has
 * left the feed (reading list, "Continue reading"). Added in schema version 4.
 */
@Entity(tableName = "news_history")
data class NewsHistoryEntity(
    /** Canonical article URL (tracking parameters removed). */
    @PrimaryKey val url: String,
    /** The link as published, which is what "Open online" opens. */
    @ColumnInfo(name = "link") val link: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    val title: String,
    val summary: String,
    @ColumnInfo(name = "image_url") val imageUrl: String?,
    @ColumnInfo(name = "published_at") val publishedAt: Long?,
    val author: String?,
    /** When the story was last opened (in the reader or online); `null` if never. */
    @ColumnInfo(name = "read_at") val readAt: Long? = null,
    /** When the story was added to the reading list; `null` if it isn't on it. */
    @ColumnInfo(name = "saved_at") val savedAt: Long? = null,
)

@Dao
interface NewsHistoryDao {

    @Query("SELECT * FROM news_history")
    fun observeAll(): Flow<List<NewsHistoryEntity>>

    @Query("SELECT * FROM news_history WHERE url = :url")
    suspend fun find(url: String): NewsHistoryEntity?

    @Upsert
    suspend fun upsert(entry: NewsHistoryEntity)

    @Query("DELETE FROM news_history WHERE url = :url")
    suspend fun delete(url: String)

    /** Forgets stories that aren't saved and weren't opened since [cutoff]. */
    @Query("DELETE FROM news_history WHERE saved_at IS NULL AND (read_at IS NULL OR read_at < :cutoff)")
    suspend fun deleteUnsavedOlderThan(cutoff: Long)

    /** Applies [change] to the entry for [url] (created from [initial] if missing), atomically. */
    @Transaction
    suspend fun update(url: String, initial: () -> NewsHistoryEntity, change: (NewsHistoryEntity) -> NewsHistoryEntity?) {
        val updated = change(find(url) ?: initial())
        if (updated == null) delete(url) else upsert(updated)
    }
}
