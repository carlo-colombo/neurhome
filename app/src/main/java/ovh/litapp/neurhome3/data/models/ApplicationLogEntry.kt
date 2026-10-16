package ovh.litapp.neurhome3.data.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey


@Entity(
    indices = [
        Index(unique = false,
            value = arrayOf("timestamp")
        ),
        Index(unique = false,
            value = arrayOf("user","packageName","timestamp")
        ),
        // Covers getUniqueSSIDs(), which filters on wifi IS NOT NULL and groups by
        // wifi over the whole table. Without it the planner does a full table scan
        // plus a temp b-tree; with it, a covering-index search. Measured on a
        // 2-year dataset: ~5.3 ms -> ~0.9 ms for ~0.6 MB of index.
        Index(unique = false,
            value = arrayOf("wifi")
        )
    ]
)
data class ApplicationLogEntry(
    @PrimaryKey(autoGenerate = true) val uid: Int = 0,
    @ColumnInfo val packageName: String,
    @ColumnInfo val timestamp: String,
    @ColumnInfo val wifi: String?,
    @ColumnInfo(defaultValue = "'UNKNOWN'")
    val wifiState: WifiContextState = WifiContextState.UNKNOWN,
    @ColumnInfo val latitude: Double?,
    @ColumnInfo val longitude: Double?,
    @ColumnInfo val geohash: String?,
    @ColumnInfo(defaultValue = "0") val user: Int,
    @ColumnInfo val query: String? = null
)
