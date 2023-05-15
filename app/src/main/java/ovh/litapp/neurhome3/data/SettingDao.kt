package ovh.litapp.neurhome3.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SettingDao {
    @Query("SELECT * FROM setting")
    fun getAll(): List<Setting>

    @Insert
    fun insertAll(vararg setting: Setting)
}