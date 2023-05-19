package ovh.litapp.neurhome3.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingDao {
    @Query("SELECT * FROM setting")
    fun getAll(): List<Setting>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(setting: Setting)

    @Query("SELECT * FROM setting WHERE `key` like :c ")
    fun like(c: String): Flow<List<Setting>>
}