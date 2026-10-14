package ovh.litapp.neurhome3.data.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime

/**
 * [Event.isMultiDay] drives the per-day row expansion in `CalendarDAO`, so the 24h boundary and
 * the null guards are pinned here. Both timestamps are `LocalDateTime`, so the check measures
 * wall-clock length and ignores zone/DST information.
 */
class EventTest {
    @Test
    fun aTwentyFourHourEventIsNotMultiDay() {
        val start = LocalDateTime.of(2024, 1, 1, 10, 0)
        val end = start.plusHours(24)
        val event = event(start = start, end = end)

        assertEquals(24L, Duration.between(start, end).toHours())
        assertFalse(event.isMultiDay)
    }

    @Test
    fun aTwentyFiveHourEventIsMultiDay() {
        val start = LocalDateTime.of(2024, 1, 1, 10, 0)
        val event = event(start = start, end = start.plusHours(25))

        assertTrue(event.isMultiDay)
    }

    @Test
    fun aMissingEndIsNotMultiDay() {
        assertFalse(event(start = LocalDateTime.of(2024, 1, 1, 10, 0), end = null).isMultiDay)
    }

    @Test
    fun aMissingOriginalStartIsNotMultiDay() {
        val start = LocalDateTime.of(2024, 1, 1, 10, 0)
        val event = Event(
            title = "synthetic",
            dtStart = start,
            end = start.plusHours(48),
            originalDtStart = null
        )

        assertFalse(event.isMultiDay)
    }

    @Test
    fun aWallClockDayAcrossTheDstFallBackIsNotMultiDay() {
        // Europe/Berlin falls back on 2024-10-27, so this local span is 24h of clock time but
        // 25h of elapsed time. `Duration.between` on two `LocalDateTime`s sees only the former,
        // which is why a one-day event stays rendered as one row.
        val start = LocalDateTime.of(2024, 10, 27, 0, 0)
        val end = LocalDateTime.of(2024, 10, 28, 0, 0)
        val event = event(start = start, end = end)

        assertEquals(24L, Duration.between(start, end).toHours())
        assertFalse(event.isMultiDay)
    }

    @Test
    fun theFirstDayOfAMultiDayEventMatchesTheOriginalStartDate() {
        val start = LocalDateTime.of(2024, 1, 1, 22, 0)
        val event = event(start = start, end = start.plusHours(26))

        assertTrue(event.isMultiDay)
        assertTrue(event.isFirstDayOfMultiDay)
    }

    @Test
    fun laterDaysOfAMultiDayEventAreNotTheFirstDay() {
        val start = LocalDateTime.of(2024, 1, 1, 22, 0)
        val event = event(start = start, end = start.plusHours(26))

        val secondDay = event.copy(dtStart = start.plusDays(1))

        assertTrue(secondDay.isMultiDay)
        assertFalse(secondDay.isFirstDayOfMultiDay)
    }

    @Test
    fun aSingleDayEventIsNeverTheFirstDayOfMultiDay() {
        val start = LocalDateTime.of(2024, 1, 1, 10, 0)
        val event = event(start = start, end = start.plusHours(2))

        assertFalse(event.isMultiDay)
        assertFalse(event.isFirstDayOfMultiDay)
    }

    private fun event(start: LocalDateTime, end: LocalDateTime?) = Event(
        title = "synthetic",
        dtStart = start,
        end = end,
        originalDtStart = start
    )
}
