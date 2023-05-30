package ovh.litapp.neurhome3.data

import java.time.LocalDateTime

data class Event(val title: String, val dtStart: LocalDateTime, val id: Long = 0)