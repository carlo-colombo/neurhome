package ovh.litapp.neurhome3.data.models

import androidx.room.Entity
import androidx.room.Index

@Entity(
    primaryKeys = ["packageName", "profile", "tagName"],
    // The primary key cannot serve `DELETE ... WHERE tagName = :tagName` (deleteForTag) or the
    // tagName lookups, because tagName is its trailing column; this index leads with tagName.
    indices = [Index("tagName")]
)
data class ApplicationTag(
    val packageName: String,
    val profile: Int,
    val tagName: String,
)
