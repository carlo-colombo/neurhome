package ovh.litapp.neurhome3.data.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

enum class HiddenPackageType {
    TOP, FILTERED
}

@Entity(primaryKeys = ["packageName", "user"], indices = [
    Index(unique = false, value = arrayOf("hideFrom"))
])
data class AdditionalPackageMetadata(
    val packageName: String,
    @ColumnInfo(defaultValue = "0") val user: Int,
    val hideFrom: HiddenPackageType?,
    val alias: String?
)
