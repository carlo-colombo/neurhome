package ovh.litapp.neurhome3.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
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
    CalendarContract.Events.CALENDAR_COLOR
)

// The indices for the projection array above.
private const val PROJECTION_ID_INDEX: Int = 0
private const val PROJECTION_ACCOUNT_NAME_INDEX: Int = 1
private const val PROJECTION_DISPLAY_NAME_INDEX: Int = 2
private const val PROJECTION_OWNER_ACCOUNT_INDEX: Int = 3

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
        val selection = "(${CalendarContract.Events.DTSTART} >= ?)"
        val events = mutableListOf<Event>()

        context.contentResolver.query(
            uri,
            EVENT_PROJECTION,
            selection,
            arrayOf(Instant.now().toEpochMilli().toString()),
            "${CalendarContract.Events.DTSTART} ASC"
        )?.use { cur ->
            var i = 0
            while (cur.moveToNext()) {
                val calID = cur.getLong(PROJECTION_ID_INDEX)
                val displayName = cur.getString(PROJECTION_DISPLAY_NAME_INDEX)
                val accountName = cur.getString(PROJECTION_ACCOUNT_NAME_INDEX)
                val ownerName = cur.getString(PROJECTION_OWNER_ACCOUNT_INDEX)
                val title = cur.getStringOrNull(4)
                val dtime = cur.getLongOrNull(5)
                val allDay = cur.getStringOrNull(6) == "1"
                val calendarColor = Color( cur.getInt(7))

                Log.d(
                    ovh.litapp.neurhome3.TAG,
                    "$calID $displayName $accountName $ownerName $title $dtime allDay:'$allDay'"
                )
                if (title != null && dtime != null) {
                    events.add(
                        Event(
                            title,
                            LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(dtime),
                                ZoneId.systemDefault(),
                            ),
                            calID,
                            allDay,
                            calendarColor
                        )
                    )
                }
                if (i++ > 10) break
            }
        }
        return events
    }
}

internal fun checkPermission(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
}
