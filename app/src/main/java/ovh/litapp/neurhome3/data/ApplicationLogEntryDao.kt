package ovh.litapp.neurhome3.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ApplicationLogEntryDao {
    @Insert
    fun insert(entry: ApplicationLogEntry)

    @Query("""
        SELECT packageName 
        FROM applicationLogEntry
        GROUP BY packageName
        ORDER BY count(packageName) desc
    """)
    fun topApps(): Flow<List<String>>
}