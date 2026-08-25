package ovh.litapp.neurhome3.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ovh.litapp.neurhome3.data.models.ApplicationTag

@Dao
interface ApplicationTagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(assignment: ApplicationTag): Long

    @Query("DELETE FROM ApplicationTag WHERE packageName = :packageName AND profile = :profile AND tagName = :tagName")
    suspend fun delete(packageName: String, profile: Int, tagName: String)

    @Query("SELECT * FROM ApplicationTag")
    fun list(): Flow<List<ApplicationTag>>

    @Query("SELECT tagName FROM ApplicationTag WHERE packageName = :packageName AND profile = :profile ORDER BY tagName COLLATE NOCASE")
    fun listForApplication(packageName: String, profile: Int): Flow<List<String>>
}
