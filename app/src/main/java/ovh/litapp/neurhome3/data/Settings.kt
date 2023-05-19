package ovh.litapp.neurhome3.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Setting(
    @PrimaryKey val key: String,
    @ColumnInfo val value: String
)