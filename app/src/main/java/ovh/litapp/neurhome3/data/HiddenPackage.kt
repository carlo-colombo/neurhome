package ovh.litapp.neurhome3.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class HiddenPackage(
    @PrimaryKey
    val packageName: String
)
