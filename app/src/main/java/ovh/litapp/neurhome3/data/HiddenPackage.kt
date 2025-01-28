package ovh.litapp.neurhome3.data

import androidx.room.ColumnInfo
import androidx.room.Entity

enum class HiddenPackageType {
    TOP, FILTERED
}

@Entity(primaryKeys = ["packageName", "user"])
data class HiddenPackage(
    val packageName: String,
    @ColumnInfo(defaultValue = "0") val user: Int,
    @ColumnInfo(defaultValue = "FILTERED") val from: HiddenPackageType = HiddenPackageType.FILTERED
)
