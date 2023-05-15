package ovh.litapp.neurhome3.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Setting(
    @PrimaryKey val uid: Int,
    @ColumnInfo(name = "key") val key: String?,
    @ColumnInfo(name = "value") val value: String?
)