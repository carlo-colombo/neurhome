package ovh.litapp.neurhome3.data.models

import androidx.room.Entity

@Entity(primaryKeys = ["packageName", "profile", "tagName"])
data class ApplicationTag(
    val packageName: String,
    val profile: Int,
    val tagName: String,
)
