package ovh.litapp.neurhome3.data

import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import kotlinx.coroutines.delay
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
    CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART
)

// The indices for the projection array above.
private const val PROJECTION_ID_INDEX: Int = 0
private const val PROJECTION_ACCOUNT_NAME_INDEX: Int = 1
private const val PROJECTION_DISPLAY_NAME_INDEX: Int = 2
private const val PROJECTION_OWNER_ACCOUNT_INDEX: Int = 3

class CalendarRepository(val context: NeurhomeApplication) {
    val events = flow {
        while (true) {
            emit(getEvents())
            delay(Duration.ofMinutes(5).toMillis())
        }
    }

    private fun getEvents(): List<Event> {
        val uri: Uri = CalendarContract.Events.CONTENT_URI
        // val selection: String =
        //     "((${CalendarContract.Calendars.ACCOUNT_NAME} = ?) AND (" + "${CalendarContract.Calendars.ACCOUNT_TYPE} = ?) AND (" + "${CalendarContract.Calendars.OWNER_ACCOUNT} = ?))"
        // val selectionArgs: Array<String> =
        //     arrayOf("hera@example.com", "com.example", "hera@example.com")

        val selection = "(${CalendarContract.Events.DTSTART} >= ?)";

        val cur: Cursor? = context.contentResolver.query(
            uri,
            EVENT_PROJECTION,
            selection,
            arrayOf(Instant.now().toEpochMilli().toString()),
            "${CalendarContract.Events.DTSTART} ASC"
        )
        var i = 0
        val events = mutableListOf<Event>()
        while (cur!!.moveToNext()) {
            // Get the field values
            val calID: Long = cur.getLong(PROJECTION_ID_INDEX)
            val displayName: String = cur.getString(PROJECTION_DISPLAY_NAME_INDEX)
            val accountName: String = cur.getString(PROJECTION_ACCOUNT_NAME_INDEX)
            val ownerName: String = cur.getString(PROJECTION_OWNER_ACCOUNT_INDEX)
            val title = cur.getStringOrNull(4)
            val dtime = cur.getLongOrNull(5)
            // Do something with the values...

            Log.d(
                ovh.litapp.neurhome3.TAG,
                "$calID $displayName $accountName $ownerName $title $dtime"
            )
            if (title != null && dtime != null) {
                events.add(
                    Event(
                        title,
                        LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(dtime),
                            ZoneId.systemDefault(),
                        ),
                        calID
                    )
                )
            }
            if (i++ > 10) break
        }
        return events
    }

}