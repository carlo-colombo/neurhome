package ovh.litapp.neurhome3.data.solar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

/**
 * [SolarCalculator] is pure JDK math behind the Home Sun card, so every case here
 * runs on the JVM with no device or database.
 */
class SolarCalculatorTest {
    @Test
    fun parisSummerSolsticeMatchesThePublishedTimes() {
        val times = SolarCalculator.calculateSolarTimes(
            latitude = 48.8566,
            longitude = 2.3522,
            date = LocalDate.of(2024, 6, 21),
            zoneId = ZoneId.of("Europe/Paris")
        )

        assertTimeNear(LocalTime.of(5, 47), times.sunrise)
        assertTimeNear(LocalTime.of(21, 58), times.sunset)
    }

    @Test
    fun newYorkWinterDayMatchesThePublishedTimes() {
        val times = SolarCalculator.calculateSolarTimes(
            latitude = 40.7128,
            longitude = -74.006,
            date = LocalDate.of(2024, 1, 15),
            zoneId = ZoneId.of("America/New_York")
        )

        assertTimeNear(LocalTime.of(7, 19), times.sunrise)
        assertTimeNear(LocalTime.of(16, 53), times.sunset)
    }

    @Test
    fun solarEventsRunInOrderFromMorningBlueHourToEveningBlueHour() {
        val times = SolarCalculator.calculateSolarTimes(
            latitude = 48.8566,
            longitude = 2.3522,
            date = LocalDate.of(2024, 6, 21),
            zoneId = ZoneId.of("Europe/Paris")
        )

        val chain = listOf(
            times.morningBlueHourStart,
            times.morningBlueHourEnd,
            times.morningGoldenHourStart,
            times.sunrise,
            times.morningGoldenHourEnd,
            times.eveningGoldenHourStart,
            times.sunset,
            times.eveningGoldenHourEnd,
            times.eveningBlueHourStart,
            times.eveningBlueHourEnd
        )
        chain.forEach { assertNotNull("a June day in Paris has no polar window", it) }

        val ordered = chain.filterNotNull()
        assertEquals(ordered.sorted(), ordered)
        // ordered is [morningBlueHourStart, …, sunrise, …, sunset, …, eveningBlueHourEnd].
        assertTrue(ordered.first() < ordered[3])
        assertTrue(ordered[3] < ordered[6])
        assertTrue(ordered[6] < ordered.last())
        // The -4° and -6° thresholds are shared between the neighbouring markers.
        assertEquals(times.morningBlueHourEnd, times.morningGoldenHourStart)
        assertEquals(times.eveningBlueHourStart, times.eveningGoldenHourEnd)
    }

    @Test
    fun eastLongitudeShiftsSolarTimesEarlierThanWestLongitude() {
        val date = LocalDate.of(2024, 3, 20)
        val utc = ZoneId.of("UTC")

        val east = SolarCalculator.calculateSolarTimes(0.0, 15.0, date, utc)
        val west = SolarCalculator.calculateSolarTimes(0.0, -15.0, date, utc)

        // Solar noon follows the sign of the longitude: east of Greenwich the sun rises and
        // sets before a location the same distance west of it.
        assertTimeNear(LocalTime.of(5, 5), east.sunrise)
        assertTimeNear(LocalTime.of(17, 12), east.sunset)
        assertTimeNear(LocalTime.of(7, 5), west.sunrise)
        assertTimeNear(LocalTime.of(19, 12), west.sunset)
        assertTrue(east.sunrise!! < west.sunrise!!)
        assertTrue(east.sunset!! < west.sunset!!)
    }

    @Test
    fun polarNightReturnsNullsWithoutThrowing() {
        val times = SolarCalculator.calculateSolarTimes(
            latitude = 78.22,
            longitude = 15.63,
            date = LocalDate.of(2024, 12, 21),
            zoneId = ZoneId.of("UTC")
        )

        assertNull(times.sunrise)
        assertNull(times.sunset)
        assertNull(times.morningBlueHourStart)
        assertNull(times.morningBlueHourEnd)
        assertNull(times.eveningBlueHourStart)
        assertNull(times.eveningBlueHourEnd)
        assertNull(times.morningGoldenHourStart)
        assertNull(times.morningGoldenHourEnd)
        assertNull(times.eveningGoldenHourStart)
        assertNull(times.eveningGoldenHourEnd)
    }

    @Test
    fun theSameSolarInstantIsRenderedInTheRequestedZone() {
        val date = LocalDate.of(2024, 6, 21)

        val utc = SolarCalculator.calculateSolarTimes(48.8566, 2.3522, date, ZoneId.of("UTC"))
        val paris = SolarCalculator.calculateSolarTimes(
            latitude = 48.8566,
            longitude = 2.3522,
            date = date,
            zoneId = ZoneId.of("Europe/Paris")
        )

        // Paris is UTC+2 in June, so every local clock time is two hours later than in UTC.
        assertEquals(utc.sunrise!!.plusHours(2), paris.sunrise)
        assertEquals(utc.sunset!!.plusHours(2), paris.sunset)
        assertEquals(utc.morningBlueHourStart!!.plusHours(2), paris.morningBlueHourStart)
        assertEquals(utc.eveningBlueHourEnd!!.plusHours(2), paris.eveningBlueHourEnd)
    }

    private fun assertTimeNear(expected: LocalTime, actual: LocalTime?, toleranceSeconds: Long = 150) {
        assertNotNull("expected a time within ${toleranceSeconds}s of $expected but was null", actual)
        val difference = abs(Duration.between(expected, actual).seconds)
        assertTrue(
            "expected $actual to be within ${toleranceSeconds}s of $expected (was ${difference}s off)",
            difference <= toleranceSeconds
        )
    }
}
