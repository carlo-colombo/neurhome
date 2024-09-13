package ovh.litapp.neurhome3.data.dao

import android.provider.CalendarContract
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import ovh.litapp.neurhome3.NeurhomeApplication
import ovh.litapp.neurhome3.data.Event
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit


private val INSTANCE_PROJECTION: Array<String> = arrayOf(
    CalendarContract.Instances._ID,                     // 0
    CalendarContract.Instances.OWNER_ACCOUNT,            // 1
    CalendarContract.Instances.CALENDAR_DISPLAY_NAME,   // 2
    CalendarContract.Instances.OWNER_ACCOUNT,    // 3
    CalendarContract.Instances.TITLE,
    CalendarContract.Instances.BEGIN,
    CalendarContract.Instances.ALL_DAY,
    CalendarContract.Instances.CALENDAR_COLOR,
    CalendarContract.Instances.RRULE,
    CalendarContract.Instances.EVENT_ID,
    CalendarContract.Instances.ORIGINAL_ID,
    CalendarContract.Instances.END,
    CalendarContract.Instances.DURATION
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
                val calID = cur.getLong(0)
                val title = cur.getStringOrNull(4)
                val dtime = cur.getLongOrNull(5)
                val allDay = cur.getStringOrNull(6) == "1"
                val calendarColor = Color(cur.getInt(7))
                val end = cur.getLongOrNull(11)

                Log.d(
                    TAG, "$title: $calID / ${cur.getLong(9)} / ${cur.getString(2)}"
                )

                if (title == null) continue
                if (dtime == null) continue

                val dtStart = localDateTime(dtime)
                events.add(
                    Event(
                        title,
                        dtStart,
                        end?.let(::localDateTime),
                        calID,
                        allDay,
                        calendarColor,
                        eventId = cur.getLong(9),
                        timestamp = dtime
                    )
                )
            }
        }

        return events.sortedBy { it.timestamp }
    }

    private fun localDateTime(time: Long): LocalDateTime = LocalDateTime.ofInstant(
        Instant.ofEpochMilli(time),
        ZoneId.systemDefault(),
    )
}