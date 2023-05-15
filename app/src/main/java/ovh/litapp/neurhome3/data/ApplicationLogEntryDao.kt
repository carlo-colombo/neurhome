package ovh.litapp.neurhome3.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ApplicationLogEntryDao {
    @Insert
    fun insert(entry: ApplicationLogEntry)

    @Query("SELECT * FROM applicationLogEntry LIMIT :c")
    fun topApps(c: Int): Flow<List<ApplicationLogEntry>>
}