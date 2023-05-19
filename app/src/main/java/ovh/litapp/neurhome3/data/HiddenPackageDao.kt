package ovh.litapp.neurhome3.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HiddenPackageDao {
    @Insert
    fun insert(hiddenPackage: HiddenPackage)

    @Delete
    fun delete(hiddenPackage: HiddenPackage)

    @Query("SELECT packageName from hiddenpackage")
    fun list(): Flow<List<String>>
}