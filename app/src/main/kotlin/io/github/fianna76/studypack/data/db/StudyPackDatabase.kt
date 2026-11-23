package io.github.fianna76.studypack.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Packs, questions, notes and answer history. Schemas are exported to `app/schemas/` so future
 * versions can ship tested migrations instead of destructive upgrades.
 */
@Database(
    entities = [
        ModuleEntity::class,
        PackEntity::class,
        TopicEntity::class,
        QuestionEntity::class,
        NoteFileEntity::class,
        QuestionStatEntity::class,
        SessionResultEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        // 1 → 2: note_files.rsvpWord (speed reading resumes at the exact word).
        AutoMigration(from = 1, to = 2),
    ],
)
@TypeConverters(Converters::class)
abstract class StudyPackDatabase : RoomDatabase() {

    abstract fun libraryDao(): LibraryDao

    abstract fun statsDao(): StatsDao

    companion object {
        private const val NAME = "studypack.db"

        fun create(context: Context): StudyPackDatabase =
            Room.databaseBuilder(context, StudyPackDatabase::class.java, NAME).build()

        fun inMemory(context: Context): StudyPackDatabase =
            Room.inMemoryDatabaseBuilder(context, StudyPackDatabase::class.java).allowMainThreadQueries().build()
    }
}

/** Lists of strings are stored as JSON arrays. */
class Converters {
    private val serializer = ListSerializer(String.serializer())

    @TypeConverter
    fun fromList(value: List<String>): String = Json.encodeToString(serializer, value)

    @TypeConverter
    fun toList(value: String): List<String> = if (value.isEmpty()) emptyList() else Json.decodeFromString(serializer, value)
}
