package ovh.litapp.neurhome3.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ApplicationLogEntryDao {
    @Insert
    fun insert(entry: ApplicationLogEntry)

    @Query(
        """
        SELECT packageName 
        FROM applicationLogEntry
        WHERE packageName NOT IN (
            SELECT packageName 
            FROM HiddenPackage)
        GROUP BY packageName
        ORDER BY count(packageName) desc
    """
    )
    fun topApps(): Flow<List<String>>


    @Transaction
    fun resetAndImport(entries: List<ApplicationLogEntry>) {
        insertAll(entries)
    }

    @Query("DELETE FROM applicationLogEntry")
    fun cleanUp()

    @Insert
    fun insertAll(entries: List<ApplicationLogEntry>)
}