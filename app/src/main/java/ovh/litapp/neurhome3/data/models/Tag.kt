package ovh.litapp.neurhome3.data.models

import androidx.room.Entity

@Entity
data class Tag(
    @androidx.room.PrimaryKey val name: String,
    @androidx.room.ColumnInfo(defaultValue = "0") val position: Int = 0,
)
