package ovh.litapp.neurhome3.data

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(primaryKeys = ["packageName", "user"])
data class HiddenPackage(
    val packageName: String,
    @ColumnInfo(defaultValue = "0") val user: Int
)
