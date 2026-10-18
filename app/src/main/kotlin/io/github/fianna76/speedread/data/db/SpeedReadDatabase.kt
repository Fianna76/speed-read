package io.github.fianna76.speedread.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The library database. Schemas are exported to `app/schemas/` so future versions can ship
 * tested migrations (`@AutoMigration` or `Migration`) instead of destructive upgrades.
 */
@Database(
    entities = [BookEntity::class, ChapterEntity::class, NewsHistoryEntity::class, BookmarkEntity::class],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        // 1 → 2: books.format column (PDF support); existing rows default to EPUB.
        AutoMigration(from = 1, to = 2),
        // 2 → 3: books.source_url column (Newsstand articles); existing rows are books (NULL).
        AutoMigration(from = 2, to = 3),
        // 3 → 4: news_history table (read marks, reading list, continue reading).
        AutoMigration(from = 3, to = 4),
        // 4 → 5: bookmarks table.
        AutoMigration(from = 4, to = 5),
    ],
)
abstract class SpeedReadDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao

    abstract fun newsHistoryDao(): NewsHistoryDao

    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        private const val NAME = "speedread.db"

        fun create(context: Context): SpeedReadDatabase =
            Room.databaseBuilder(context, SpeedReadDatabase::class.java, NAME).build()
    }
}
