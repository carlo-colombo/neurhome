package ovh.litapp.neurhome3.data

import androidx.room.ColumnInfo
import androidx.room.Entity

enum class HiddenPackageType {
    TOP, FILTERED
}

@Entity(primaryKeys = ["packageName", "user"])
data class AdditionalPackageMetadata(
    val packageName: String,
    @ColumnInfo(defaultValue = "0") val user: Int,
    val hideFrom: HiddenPackageType?,
    val alias: String?
)
