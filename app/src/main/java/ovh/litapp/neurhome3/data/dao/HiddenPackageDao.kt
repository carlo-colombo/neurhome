package ovh.litapp.neurhome3.data.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import ovh.litapp.neurhome3.data.AdditionalPackageMetadata

@Dao
interface HiddenPackageDao {
    @Insert
    fun insert(additionalPackageMetadata: AdditionalPackageMetadata)

    @Delete
    fun delete(additionalPackageMetadata: AdditionalPackageMetadata)

    @Upsert
    fun upsert(additionalPackageMetadata: AdditionalPackageMetadata)

    @Query("SELECT * from additionalpackagemetadata")
    fun list(): Flow<List<AdditionalPackageMetadata>>

    fun toggle(additionalPackageMetadata: AdditionalPackageMetadata) {
        try {
            this.insert(additionalPackageMetadata)
        } catch (e: SQLiteConstraintException) {
            try {
                this.delete(additionalPackageMetadata)
            } catch (e: Exception) {
                throw e
            }
        }
    }
}