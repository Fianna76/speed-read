package io.github.fianna76.speedread.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Reading done on one local day: words shown while playing, and time spent. Added in schema version 7. */
@Entity(tableName = "reading_days")
data class ReadingDayEntity(
    /** ISO date, e.g. `2026-10-18`. */
    @PrimaryKey val date: String,
    val words: Long,
    @ColumnInfo(name = "millis") val millis: Long,
)

@Dao
interface ReadingDayDao {

    @Query("SELECT * FROM reading_days ORDER BY date")
    fun observeAll(): Flow<List<ReadingDayEntity>>

    @Query("SELECT * FROM reading_days WHERE date = :date")
    suspend fun find(date: String): ReadingDayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(day: ReadingDayEntity)

    /** Adds to [date]'s totals (no UPSERT ... DO UPDATE on API 26's SQLite). */
    @Transaction
    suspend fun add(date: String, words: Long, millis: Long) {
        val day = find(date)
        put(ReadingDayEntity(date, (day?.words ?: 0) + words, (day?.millis ?: 0) + millis))
    }
}
