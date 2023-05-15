package ovh.litapp.neurhome3.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ApplicationLogEntry(
    @PrimaryKey val uid: Int,
    @ColumnInfo val packageName: String,
    @ColumnInfo val timestamp: String,
    @ColumnInfo val wifi: String,
    @ColumnInfo val latitude: Float,
    @ColumnInfo val longitude: Float
)
