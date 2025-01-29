package ovh.litapp.neurhome3.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import ovh.litapp.neurhome3.data.AdditionalPackageMetadata
import ovh.litapp.neurhome3.data.HiddenPackageType

@Dao
interface AdditionalPackageMetadataDao {
    @Insert
    fun insert(additionalPackageMetadata: AdditionalPackageMetadata)

    @Delete
    fun delete(additionalPackageMetadata: AdditionalPackageMetadata)

    @Upsert(entity = AdditionalPackageMetadata::class)
    fun upsert(updateVisibility: UpdateVisibility)

    @Upsert(entity = AdditionalPackageMetadata::class)
    fun upsert(setUpdateAlias: UpdateAlias)

    @Query("SELECT * from additionalpackagemetadata")
    fun list(): Flow<List<AdditionalPackageMetadata>>
}

data class UpdateAlias(
    val packageName: String,
    val user: Int,
    val alias: String
)

data class UpdateVisibility(
    val packageName: String,
    val user: Int,
    val hideFrom: HiddenPackageType?
)