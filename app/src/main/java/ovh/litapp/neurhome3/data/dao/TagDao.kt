package ovh.litapp.neurhome3.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ovh.litapp.neurhome3.data.models.Tag

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: Tag): Long

    @Query("SELECT * FROM Tag ORDER BY position, name COLLATE NOCASE")
    fun list(): Flow<List<Tag>>

    @Query("UPDATE Tag SET position = :position WHERE name = :name")
    suspend fun updatePosition(name: String, position: Int)

    @Query("DELETE FROM Tag WHERE name = :name")
    suspend fun delete(name: String)
}
