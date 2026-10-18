package io.github.fianna76.speedread.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** A shelf the user groups books on ("To read", "Book club"). Added in schema version 6. */
@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** A book on a collection; a book can be on several. Removed with either side. */
@Entity(
    tableName = "book_collections",
    primaryKeys = ["collection_id", "book_id"],
    indices = [Index("book_id")],
    foreignKeys = [
        ForeignKey(entity = CollectionEntity::class, parentColumns = ["id"], childColumns = ["collection_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["book_id"], onDelete = ForeignKey.CASCADE),
    ],
)
data class BookCollectionEntity(
    @ColumnInfo(name = "collection_id") val collectionId: Long,
    @ColumnInfo(name = "book_id") val bookId: Long,
)

@Dao
interface CollectionDao {

    @Query("SELECT * FROM collections ORDER BY created_at")
    fun observeCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM book_collections")
    fun observeMemberships(): Flow<List<BookCollectionEntity>>

    @Query("SELECT * FROM collections ORDER BY created_at")
    suspend fun allCollections(): List<CollectionEntity>

    @Query("SELECT * FROM book_collections")
    suspend fun allMemberships(): List<BookCollectionEntity>

    @Insert
    suspend fun insertCollection(collection: CollectionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMemberships(memberships: List<BookCollectionEntity>)

    @Query("DELETE FROM book_collections WHERE collection_id = :collectionId AND book_id IN (:bookIds)")
    suspend fun deleteMemberships(collectionId: Long, bookIds: Collection<Long>)

    @Query("UPDATE collections SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteCollection(id: Long)
}
