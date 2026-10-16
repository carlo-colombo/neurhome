package ovh.litapp.neurhome3.data.repositories

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogRetentionTest {
    @Test
    fun cutoffIsExactlyTwoYearsBeforeNow() {
        val now = LocalDateTime.of(2026, 9, 30, 11, 24, 24, 208_958_000)

        assertEquals("2024-09-30T11:24:24.208958", retentionCutoff(now))
    }

    @Test
    fun cutoffClampsLeapDayToTheLastValidDay() {
        val now = LocalDateTime.of(2024, 2, 29, 10, 0, 0)

        assertEquals("2022-02-28T10:00:00", retentionCutoff(now))
    }

    @Test
    fun cutoffOrdersChronologicallyUnderLexicographicComparison() {
        val cutoff = retentionCutoff(LocalDateTime.of(2026, 9, 30, 11, 24, 24, 208_958_000))

        assertTrue("2024-09-30T11:24:24.208957" < cutoff)
        assertEquals("2024-09-30T11:24:24.208958", cutoff)
        assertFalse("2024-09-30T11:24:24.208959" < cutoff)
    }

    @Test
    fun vacuumIsSkippedWhileTheFreelistIsSmall() {
        assertFalse(isVacuumWorthwhile(freelistCount = 0, pageCount = 6367))
        assertFalse(isVacuumWorthwhile(freelistCount = 1591, pageCount = 6367))
    }

    @Test
    fun vacuumRunsOnceTheFreelistReachesTheThreshold() {
        assertTrue(isVacuumWorthwhile(freelistCount = 1592, pageCount = 6367))
        assertTrue(isVacuumWorthwhile(freelistCount = 25, pageCount = 100))
    }

    @Test
    fun vacuumIsSkippedForAnEmptyDatabase() {
        assertFalse(isVacuumWorthwhile(freelistCount = 0, pageCount = 0))
    }
}
