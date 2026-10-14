package ovh.litapp.neurhome3.data.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherForecastTest {
    @Test
    fun oneDayResponseRendersOnlyThatDay() {
        val days = forecastDays(daily(dayCount = 1))

        val day = days.single()
        assertEquals("2024-01-01", day.date)
        assertEquals(0, day.weatherCode)
        assertEquals(15.0, day.maxTemperature, 0.0)
        assertEquals(5.0, day.minTemperature, 0.0)
    }

    @Test
    fun threeDayResponseRendersThreeDays() {
        val days = forecastDays(daily(dayCount = 3))

        assertEquals(listOf("2024-01-01", "2024-01-02", "2024-01-03"), days.map { it.date })
    }

    @Test
    fun responseLongerThanTheRequestedWindowIsCappedAtFourDays() {
        val days = forecastDays(daily(dayCount = 6))

        assertEquals(4, days.size)
        assertEquals((0 until 4).toList(), days.map { it.weatherCode })
    }

    @Test
    fun emptyResponseRendersNoDays() {
        assertEquals(0, forecastDays(daily(dayCount = 0)).size)
    }

    @Test
    fun mismatchedParallelListsStopAtTheShortestOne() {
        val full = daily(dayCount = 4)
        val truncated = full.copy(weatherCode = full.weatherCode.take(2))

        val days = forecastDays(truncated)

        assertEquals(2, days.size)
        assertEquals(listOf("2024-01-01", "2024-01-02"), days.map { it.date })
    }

    private fun daily(dayCount: Int) = Daily(
        time = List(dayCount) { "2024-01-0${it + 1}" },
        weatherCode = List(dayCount) { it },
        temperatureMax = List(dayCount) { 15.0 + it },
        temperatureMin = List(dayCount) { 5.0 + it }
    )
}
