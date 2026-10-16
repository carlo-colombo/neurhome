package ovh.litapp.neurhome3.data.dao

import android.provider.CalendarContract
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import ovh.litapp.neurhome3.application.NeurhomeApplication
import ovh.litapp.neurhome3.data.models.Event
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Position of every column of [INSTANCE_PROJECTION]. The cursor is read positionally, so a column
 * may only be added or removed here together with the parse in [CalendarDAO.getNextEvents].
 */
private object InstanceColumn {
    const val ID = 0
    const val CALENDAR_DISPLAY_NAME = 1
    const val TITLE = 2
    const val BEGIN = 3
    const val ALL_DAY = 4
    const val CALENDAR_COLOR = 5
    const val EVENT_ID = 6
    const val END = 7
}

/** Exactly the columns read below, in [InstanceColumn] order. */
private val INSTANCE_PROJECTION: Array<String> = arrayOf(
    CalendarContract.Instances._ID,
    CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
    CalendarContract.Instances.TITLE,
    CalendarContract.Instances.BEGIN,
    CalendarContract.Instances.ALL_DAY,
    CalendarContract.Instances.CALENDAR_COLOR,
    CalendarContract.Instances.EVENT_ID,
    CalendarContract.Instances.END,
)

private const val TAG = "CalendarDAO"

class CalendarDAO(val context: NeurhomeApplication) {
    fun getNextEvents(days: Long = 60): List<Event> {
        val now = Instant.now()
        val startMillis: Long = now.toEpochMilli()
        val endMillis: Long = now.plus(days, ChronoUnit.DAYS).toEpochMilli()

        val events = mutableListOf<Event>()

        CalendarContract.Instances.query(
            context.contentResolver, INSTANCE_PROJECTION, startMillis, endMillis
        )?.use { cur ->
            while (cur.moveToNext()) {
                val calID = cur.getLong(InstanceColumn.ID)
                val title = cur.getStringOrNull(InstanceColumn.TITLE)
                val dtime = cur.getLongOrNull(InstanceColumn.BEGIN)
                val allDay = cur.getStringOrNull(InstanceColumn.ALL_DAY) == "1"
                val calendarColor = Color(cur.getInt(InstanceColumn.CALENDAR_COLOR))
                val end = cur.getLongOrNull(InstanceColumn.END)

                Log.d(
                    TAG,
                    "$title: $calID / ${cur.getLong(InstanceColumn.EVENT_ID)} / " +
                        cur.getString(InstanceColumn.CALENDAR_DISPLAY_NAME)
                )

                if (title == null) continue
                if (dtime == null) continue

                val dtStart = localDateTime(dtime)
                val dtEnd = end?.let(::localDateTime)
                val eventId = cur.getLong(InstanceColumn.EVENT_ID)
                val event = Event(
                    title,
                    dtStart,
                    dtEnd,
                    calID,
                    allDay,
                    calendarColor,
                    eventId = eventId,
                    timestamp = dtime,
                    originalDtStart = dtStart
                )

                if (event.isMultiDay) {
                    var current = dtStart
                    while (current.isBefore(dtEnd)) {
                        events.add(event.copy(dtStart = current))
                        current = current.plusDays(1)
                    }
                } else {
                    events.add(event)
                }
            }
        }

        return events.distinctBy { it.title to it.dtStart }
            .sortedWith(compareBy({ it.end == null }, { it.end }, { it.dtStart }))
    }

    private fun localDateTime(time: Long): LocalDateTime = LocalDateTime.ofInstant(
        Instant.ofEpochMilli(time),
        ZoneId.systemDefault(),
    )
}
