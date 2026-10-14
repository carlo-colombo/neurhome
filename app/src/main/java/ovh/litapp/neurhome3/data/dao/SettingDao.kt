package ovh.litapp.neurhome3.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import ovh.litapp.neurhome3.data.models.Setting

@Dao
interface SettingDao {
    @Query("SELECT * FROM setting")
    fun getAll(): List<Setting>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOverride(setting: Setting)

    @Upsert
    fun upsert(setting: Setting)

    @Query("SELECT * FROM setting WHERE `key` like :c ")
    fun like(c: String): Flow<List<Setting>>

    @Delete
    fun delete(setting: Setting)

    @Query("SELECT * FROM setting WHERE `key` = :s")
    fun get(s: String): Flow<List<Setting>>

    @Query("SELECT * FROM setting WHERE `key` = :s")
    fun getOnce(s: String): Setting?

    /**
     * Flips [key] between absent and stored as [value] atomically: the read and the
     * write run in one transaction instead of relying on a primary-key violation to
     * detect an existing row.
     */
    @Transaction
    fun toggle(key: String, value: String) {
        val existing = getOnce(key)
        if (existing == null) upsert(Setting(key, value)) else delete(existing)
    }
}