package ovh.litapp.neurhome3.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class HiddenPackage(
    @PrimaryKey(autoGenerate = true) val uid: Int = 0,
    @ColumnInfo val packageName: String
)
