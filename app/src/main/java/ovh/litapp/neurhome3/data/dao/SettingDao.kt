package ovh.litapp.neurhome3.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ovh.litapp.neurhome3.data.Setting

@Dao
interface SettingDao {
    @Query("SELECT * FROM setting")
    fun getAll(): List<Setting>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOverride(setting: Setting)

    @Insert
    fun insert(setting: Setting)

    @Query("SELECT * FROM setting WHERE `key` like :c ")
    fun like(c: String): Flow<List<Setting>>

    @Delete
    fun delete(setting: Setting)

    @Query("SELECT * FROM setting WHERE `key` = :s")
    fun get(s: String): Flow<List<Setting>>
}