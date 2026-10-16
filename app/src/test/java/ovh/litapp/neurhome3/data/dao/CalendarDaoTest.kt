package ovh.litapp.neurhome3.data.dao

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.ui.graphics.Color
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import ovh.litapp.neurhome3.application.NeurhomeApplication
import ovh.litapp.neurhome3.data.models.Event

/**
 * Drives [CalendarDAO.getNextEvents] with a seeded [MatrixCursor] served by a fake
 * `com.android.calendar` provider, so the per-day expansion of a multi-day event, the exclusive
 * all-day end, the null guards, the de-duplication and the sort order run as production code
 * without a device calendar.
 *
 * [ovh.litapp.neurhome3.data.models.EventTest] pins [Event.isMultiDay] in isolation; this test
 * proves that flag is what actually splits a row into one row per day, and
 * [theProjectionKeepsTheColumnsTheParserReadsByIndex] keeps the read-by-index contract honest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CalendarDaoTest {
    private lateinit var application: NeurhomeApplication
    private lateinit var provider: FakeCalendarProvider

    @Before
    fun setUp() {
        application = unstartedApplication()
        provider = FakeCalendarProvider()
        ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, provider)
    }

    @Test
    fun aMultiDayEventIsExpandedIntoOneRowPerDayAtTheSameWallClockTime() {
        val start = LocalDateTime.of(2024, 3, 1, 10, 0)
        val end = start.plusHours(49)

        val events = events(
            listOf(Row(id = 1, title = "Trip", begin = millis(start), end = millis(end)))
        )

        assertEquals(
            listOf(
                LocalDateTime.of(2024, 3, 1, 10, 0),
                LocalDateTime.of(2024, 3, 2, 10, 0),
                LocalDateTime.of(2024, 3, 3, 10, 0),
            ),
            events.map { it.dtStart },
        )
        // The expansion copies the original start into `originalDtStart`, which is what keeps the
        // later days of one event grouped in the UI.
        assertEquals(listOf(start, start, start), events.map { it.originalDtStart })
        assertEquals(listOf(end, end, end), events.map { it.end })
        assertEquals(listOf(true, false, false), events.map { it.isFirstDayOfMultiDay })
        assertEquals(listOf(10L, 10L, 10L), events.map { it.eventId })
        assertEquals(listOf(1L, 1L, 1L), events.map { it.id })
        assertEquals(Color(0x11223344), events.first().color)
        assertEquals(false, events.first().allDay)
    }

    @Test
    fun aSingleDayAllDayEventWithAnExclusiveEndStaysOneRow() {
        // CalendarContract all-day ends are exclusive: one day is exactly 24h.
        val start = LocalDateTime.of(2024, 3, 10, 0, 0)
        val exclusiveEnd = start.plusHours(24)

        val events = events(
            listOf(
                Row(
                    id = 2,
                    title = "Holiday",
                    begin = millis(start),
                    end = millis(exclusiveEnd),
                    allDay = true,
                )
            )
        )

        assertEquals(listOf(start), events.map { it.dtStart })
        assertEquals(listOf(exclusiveEnd), events.map { it.end })
        assertEquals(true, events.single().allDay)
    }

    @Test
    fun aTwoDayAllDayEventExpandsToTwoDaysAndDropsTheExclusiveEnd() {
        val start = LocalDateTime.of(2024, 3, 10, 0, 0)
        val exclusiveEnd = start.plusHours(48)

        val events = events(
            listOf(
                Row(
                    id = 3,
                    title = "Weekend",
                    begin = millis(start),
                    end = millis(exclusiveEnd),
                    allDay = true,
                )
            )
        )

        assertEquals(
            listOf(LocalDateTime.of(2024, 3, 10, 0, 0), LocalDateTime.of(2024, 3, 11, 0, 0)),
            events.map { it.dtStart },
        )
    }

    @Test
    fun rowsWithoutATitleOrABeginAreSkipped() {
        val start = LocalDateTime.of(2024, 3, 4, 9, 0)

        val events = events(
            listOf(
                Row(id = 1, title = null, begin = millis(start)),
                Row(id = 2, title = "No begin", begin = null),
                Row(id = 3, title = "Kept", begin = millis(start)),
            )
        )

        assertEquals(listOf("Kept"), events.map { it.title })
    }

    @Test
    fun rowsThatShareATitleAndStartAreDeduplicatedPerDay() {
        val start = LocalDateTime.of(2024, 3, 1, 10, 0)
        val sameStart = LocalDateTime.of(2024, 3, 6, 8, 0)

        val events = events(
            listOf(
                // The same two-day event reported twice, as two synced calendars would.
                Row(id = 1, title = "Trip", begin = millis(start), end = millis(start.plusHours(48))),
                Row(id = 2, title = "Trip", begin = millis(start), end = millis(start.plusHours(48))),
                Row(id = 3, title = "Dentist", begin = millis(sameStart)),
                Row(id = 4, title = "Dentist", begin = millis(sameStart)),
            )
        )

        assertEquals(
            listOf("Trip" to LocalDateTime.of(2024, 3, 1, 10, 0), "Trip" to LocalDateTime.of(2024, 3, 2, 10, 0)),
            events.filter { it.title == "Trip" }.map { it.title to it.dtStart },
        )
        assertEquals(
            listOf("Dentist" to sameStart),
            events.filter { it.title == "Dentist" }.map { it.title to it.dtStart },
        )
        assertEquals(3, events.size)
    }

    @Test
    fun eventsWithoutAnEndSortLast() {
        val events = events(
            listOf(
                Row(
                    id = 1,
                    title = "Later end",
                    begin = millis(LocalDateTime.of(2024, 3, 5, 9, 0)),
                    end = millis(LocalDateTime.of(2024, 3, 5, 18, 0)),
                ),
                Row(
                    id = 2,
                    title = "No end",
                    begin = millis(LocalDateTime.of(2024, 3, 5, 8, 0)),
                    end = null,
                ),
                Row(
                    id = 3,
                    title = "Next day",
                    begin = millis(LocalDateTime.of(2024, 3, 6, 8, 0)),
                    end = millis(LocalDateTime.of(2024, 3, 6, 9, 0)),
                ),
            )
        )

        // Within the ended events the end dominates the start; the end-less event is last even
        // though it starts before "Later end".
        assertEquals(listOf("Later end", "Next day", "No end"), events.map { it.title })
    }

    @Test
    fun theQueryWindowSpansTheRequestedNumberOfDays() {
        provider.cursor = cursorOf(emptyList())
        val before = Instant.now()

        CalendarDAO(application).getNextEvents(days = 2)

        val after = Instant.now()
        val (begin, end) = provider.queries.single().window()
        assertTrue("$begin must be the test's now", begin in before.toEpochMilli()..after.toEpochMilli())
        assertEquals(TimeUnit.DAYS.toMillis(2), end - begin)
    }

    @Test
    fun theDefaultQueryWindowSpansSixtyDays() {
        provider.cursor = cursorOf(emptyList())

        CalendarDAO(application).getNextEvents()

        val (begin, end) = provider.queries.single().window()
        assertEquals(TimeUnit.DAYS.toMillis(60), end - begin)
        assertTrue(begin > 0)
    }

    @Test
    fun theProjectionKeepsTheColumnsTheParserReadsByIndex() {
        provider.cursor = cursorOf(emptyList())

        CalendarDAO(application).getNextEvents()

        // The parser reads these indices directly, so a projection whose order drifts would make
        // it read the wrong column instead of failing. This is the exact order of
        // CalendarDAO's InstanceColumn constants.
        assertEquals(
            listOf(
                CalendarContract.Instances._ID,
                CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.CALENDAR_COLOR,
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.END,
            ),
            provider.queries.single().projection,
        )
    }

    private fun events(rows: List<Row>, days: Long = 60L): List<Event> {
        provider.cursor = cursorOf(rows)
        return CalendarDAO(application).getNextEvents(days)
    }

    private fun millis(time: LocalDateTime): Long =
        time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /**
     * A [NeurhomeApplication] with the framework base context attached but [Application.onCreate]
     * deliberately left unrun: [CalendarDAO] only needs the application's `contentResolver`, and
     * the real `onCreate` opens the on-disk database and starts collectors that have nothing to do
     * with event expansion.
     */
    private fun unstartedApplication(): NeurhomeApplication {
        val app = NeurhomeApplication()
        val attachBaseContext =
            ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
        attachBaseContext.isAccessible = true
        attachBaseContext.invoke(app, RuntimeEnvironment.getApplication())
        return app
    }

    private data class Row(
        val id: Long,
        val title: String?,
        val begin: Long?,
        val end: Long? = null,
        val allDay: Boolean = false,
    )

    /** Builds a cursor in the same column order as [CalendarDaoTest.theProjectionKeepsTheColumnsTheParserReadsByIndex]. */
    private fun cursorOf(rows: List<Row>): MatrixCursor {
        val cursor = MatrixCursor(CalendarDaoTest.COLUMNS)
        rows.forEach { row ->
            val values = arrayOfNulls<Any?>(COLUMNS.size)
            values[0] = row.id
            values[1] = "Personal"
            values[2] = row.title
            values[3] = row.begin
            values[4] = if (row.allDay) "1" else "0"
            values[5] = 0x11223344
            values[6] = row.id * 10
            values[7] = row.end
            cursor.addRow(values)
        }
        return cursor
    }

    private class FakeCalendarProvider : ContentProvider() {
        data class Query(val uri: Uri, val projection: List<String>) {
            fun window(): Pair<Long, Long> {
                val ids = uri.pathSegments.takeLast(2).map { it.toLong() }
                return ids[0] to ids[1]
            }
        }

        val queries = mutableListOf<Query>()
        lateinit var cursor: Cursor

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            queries += Query(uri, projection?.toList().orEmpty())
            return cursor
        }

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0
    }

    private companion object {
        val COLUMNS = arrayOf(
            CalendarContract.Instances._ID,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_COLOR,
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.END,
        )
    }
}
