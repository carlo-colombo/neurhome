package ovh.litapp.neurhome3.data.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey


@Entity(
    indices = [
        Index(
            unique = false,
            value = arrayOf("packageName"),
            orders = arrayOf(Index.Order.DESC)
        ),
        Index(unique = false,
            value = arrayOf("timestamp")
        ),
        Index(unique = false,
            value = arrayOf("user","packageName","timestamp")
        )
    ]
)
data class ApplicationLogEntry(
    @PrimaryKey(autoGenerate = true) val uid: Int = 0,
    @ColumnInfo val packageName: String,
    @ColumnInfo val timestamp: String,
    @ColumnInfo val wifi: String?,
    @ColumnInfo val latitude: Double?,
    @ColumnInfo val longitude: Double?,
    @ColumnInfo val geohash: String?,
    @ColumnInfo(defaultValue = "0") val user: Int,
    @ColumnInfo val query: String? = null
)
