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
    entities = [BookEntity::class, ChapterEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        // 1 → 2: books.format column (PDF support); existing rows default to EPUB.
        AutoMigration(from = 1, to = 2),
    ],
)
abstract class SpeedReadDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao

    companion object {
        private const val NAME = "speedread.db"

        fun create(context: Context): SpeedReadDatabase =
            Room.databaseBuilder(context, SpeedReadDatabase::class.java, NAME).build()
    }
}
