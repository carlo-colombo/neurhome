package ovh.litapp.neurhome3.data

import androidx.compose.ui.graphics.Color
import java.time.LocalDateTime

data class Event(
    val title: String,
    val dtStart: LocalDateTime,
    val id: Long = 0,
    val allDay: Boolean = false,
    val color: Color = Color.Black
)