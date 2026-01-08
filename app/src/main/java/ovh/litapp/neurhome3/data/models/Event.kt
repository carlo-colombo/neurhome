package ovh.litapp.neurhome3.data.models

import androidx.compose.ui.graphics.Color
import java.time.LocalDateTime

data class Event(
    val title: String,
    val dtStart: LocalDateTime,
    val end: LocalDateTime? = null,
    val id: Long = 0,
    val allDay: Boolean = false,
    val color: Color = Color.Black,
    val eventId: Long = id,
    val timestamp: Long = 0
)