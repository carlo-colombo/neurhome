package ovh.litapp.neurhome3.data.models

import androidx.room.Entity

@Entity
data class Tag(
    @androidx.room.PrimaryKey val name: String,
)
