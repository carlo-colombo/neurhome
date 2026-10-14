package ovh.litapp.neurhome3.data.models

import androidx.compose.ui.graphics.Color
import java.time.Duration
import java.time.LocalDateTime

data class Event(
    val title: String,
    val dtStart: LocalDateTime,
    val end: LocalDateTime? = null,
    val id: Long = 0,
    val allDay: Boolean = false,
    val color: Color = Color.Black,
    val eventId: Long = id,
    val timestamp: Long = 0,
    val originalDtStart: LocalDateTime? = null
) {
    /**
     * True when the event spans more than one wall-clock day. `Duration` is measured between two
     * [LocalDateTime]s, so this is a wall-clock span (not elapsed time) and is unaffected by DST.
     * CalendarContract all-day ends are exclusive: a single all-day event is exactly 24h and stays
     * a single day, while a two-day all-day event is 48h. `CalendarDAO` expands a multi-day event
     * into one row per day.
     */
    val isMultiDay: Boolean
        get() = end != null && originalDtStart != null && Duration.between(originalDtStart, end)
            .toHours() > 24
    val isFirstDayOfMultiDay: Boolean
        get() = isMultiDay && dtStart.toLocalDate().isEqual(originalDtStart?.toLocalDate())
}