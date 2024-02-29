package ovh.litapp.neurhome3.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import org.dmfs.rfc5545.DateTime
import org.dmfs.rfc5545.recur.RecurrenceRule
import org.dmfs.rfc5545.recurrenceset.OfRule
import ovh.litapp.neurhome3.NeurhomeApplication
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

// Projection array. Creating indices for this array instead of doing
// dynamic lookups improves performance.
private val EVENT_PROJECTION: Array<String> = arrayOf(
    CalendarContract.Events._ID,                     // 0
    CalendarContract.Events.ACCOUNT_NAME,            // 1
    CalendarContract.Events.CALENDAR_DISPLAY_NAME,   // 2
    CalendarContract.Events.OWNER_ACCOUNT,    // 3
    CalendarContract.Events.TITLE,
    CalendarContract.Events.DTSTART,
    CalendarContract.Events.ALL_DAY,
    CalendarContract.Events.CALENDAR_COLOR,
    CalendarContract.Events.RRULE
)

class CalendarRepository(val context: NeurhomeApplication) {

    val events = flow {
        val i = 0
        while (true) {
            emit(i)
            delay(Duration.ofMinutes(5).toMillis())
        }
    }.combine(context.settingsRepository.showCalendar) { _, showCalendar ->
        if (showCalendar && checkPermission(context, Manifest.permission.READ_CALENDAR)) {
            getEvents()
        } else {
            listOf()
        }
    }

    private fun getEvents(): MutableList<Event> {
        val uri: Uri = CalendarContract.Events.CONTENT_URI
        val events = mutableListOf<Event>()

        context.contentResolver.query(
            uri,
            EVENT_PROJECTION,
            "",
            arrayOf(),
            "${CalendarContract.Events.DTSTART} ASC"
        )?.use { cur ->

            var i = 0
            while (cur.moveToNext()) {
                val calID = cur.getLong(0)
                val title = cur.getStringOrNull(4)
                val dtime = cur.getLongOrNull(5)
                val allDay = cur.getStringOrNull(6) == "1"
                val calendarColor = Color(cur.getInt(7))
                val rrule = cur.getString(8)

                if (title == null) continue
                if (dtime == null) continue


                if (rrule == null) {
                    val dtStart = localDateTime(dtime)
                    if (dtStart.isAfter(LocalDateTime.now())) {
                        events.add(
                            Event(
                                title,
                                dtStart,
                                calID,
                                allDay,
                                calendarColor
                            )
                        )
                        i++
                    }
                    continue
                }

                val occurrences = OfRule(RecurrenceRule(rrule), DateTime(dtime))

                occurrences.forEach { dt ->
                    val dtStart = localDateTime(dt.timestamp)
                    if (dtStart.isAfter(LocalDateTime.now())) {
                        events.add(
                            Event(
                                "$title",
                                dtStart,
                                calID,
                                allDay,
                                calendarColor
                            )
                        )
                        i++
                    }
                }

                if (i > 20) break
            }
        }
        return events
            .apply { sortBy { it.dtStart } }
            .toMutableList()
    }

    private fun localDateTime(dtime: Long): LocalDateTime = LocalDateTime.ofInstant(
        Instant.ofEpochMilli(dtime),
        ZoneId.systemDefault(),
    )
}

internal fun checkPermission(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
}
