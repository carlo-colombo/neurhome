package ovh.litapp.neurhome3.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import ovh.litapp.neurhome3.NeurhomeApplication
import ovh.litapp.neurhome3.TAG
import java.time.Duration
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
    CalendarContract.Instances.ORIGINAL_ID
)

class CalendarRepository(val context: NeurhomeApplication) {

    val events = flow {
        while (true) {
            emit(42)
            delay(Duration.ofMinutes(5).toMillis())
        }
    }.combine(context.settingsRepository.showCalendar) { _, showCalendar ->
        if (!showCalendar || !checkPermission(context, Manifest.permission.READ_CALENDAR)) {
            return@combine listOf()
        }

        getInstances()
    }

    private fun getInstances(): List<Event> {
        val now = Instant.now()
        val startMillis: Long = now.toEpochMilli()
        val endMillis: Long = now.plus(60, ChronoUnit.DAYS).toEpochMilli()

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, startMillis)
            ContentUris.appendId(it, endMillis)
        }.build()

        val events = mutableListOf<Event>()

        context.contentResolver.query(
            uri,
            INSTANCE_PROJECTION,
            "",
            arrayOf(),
            "${CalendarContract.Instances.DTSTART} ASC"
        )?.use { cur ->
            while (cur.moveToNext()) {
                val calID = cur.getLong(0)
                val title = cur.getStringOrNull(4)
                val dtime = cur.getLongOrNull(5)
                val allDay = cur.getStringOrNull(6) == "1"
                val calendarColor = Color(cur.getInt(7))

                Log.d(TAG, "$title: $calID / ${cur.getLong(9)} / ${cur.getString(2)}")

                if (title == null) continue
                if (dtime == null) continue

                val dtStart = localDateTime(dtime)
                events.add(
                    Event(
                        title, dtStart, calID, allDay, calendarColor, eventId = cur.getLong(9), timestamp = dtime
                    )
                )
            }
        }

        return events.sortedBy { it.timestamp }
    }

    private fun localDateTime(dtime: Long): LocalDateTime = LocalDateTime.ofInstant(
        Instant.ofEpochMilli(dtime),
        ZoneId.systemDefault(),
    )
}

internal fun checkPermission(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        permission
    ) == PackageManager.PERMISSION_GRANTED
}
