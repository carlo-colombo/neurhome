package ovh.litapp.neurhome3.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The watch face reads the wall clock itself now instead of riding the Home state tower's
 * `ACTION_TIME_TICK` emissions, so its delay has to land on the next minute boundary and never be
 * zero (a zero delay would spin).
 */
class WatchMinuteTickTest {
    @Test
    fun waitsUntilTheNextMinuteBoundary() {
        assertEquals(30_000L, millisUntilNextMinute(30_000L))
        assertEquals(45_000L, millisUntilNextMinute(15_000L))
        assertEquals(1L, millisUntilNextMinute(59_999L))
    }

    @Test
    fun waitsAFullMinuteOnABoundary() {
        assertEquals(60_000L, millisUntilNextMinute(0L))
        assertEquals(60_000L, millisUntilNextMinute(120_000L))
    }
}
