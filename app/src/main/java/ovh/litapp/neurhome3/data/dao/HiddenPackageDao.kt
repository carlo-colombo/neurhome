package ovh.litapp.neurhome3.data.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import ovh.litapp.neurhome3.data.HiddenPackage

@Dao
interface HiddenPackageDao {
    @Insert
    fun insert(hiddenPackage: HiddenPackage)

    @Delete
    fun delete(hiddenPackage: HiddenPackage)

    @Upsert
    fun upsert(hiddenPackage: HiddenPackage)

    @Query("SELECT * from hiddenpackage")
    fun list(): Flow<List<HiddenPackage>>

    fun toggle(hiddenPackage: HiddenPackage) {
        try {
            this.insert(hiddenPackage)
        } catch (e: SQLiteConstraintException) {
            try {
                this.delete(hiddenPackage)
            } catch (e: Exception) {
                throw e
            }
        }
    }
}