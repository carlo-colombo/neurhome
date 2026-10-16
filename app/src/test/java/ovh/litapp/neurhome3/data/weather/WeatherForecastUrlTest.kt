package ovh.litapp.neurhome3.data.weather

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.elementNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Open-Meteo request built by [WeatherForecastUrl]. A wrong parameter name is not a
 * visible failure: Open-Meteo ignores it and answers with empty `current`/`daily` payloads, which
 * [WeatherResponseJsonTest] cannot catch because it decodes a fixed document. The last two tests
 * keep the request and the decoder in step, so a field that is requested can always be decoded.
 */
class WeatherForecastUrlTest {
    @Test
    fun theForecastUrlCarriesEveryParameterAtItsDocumentedValue() {
        assertEquals(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=48.85&longitude=2.35" +
                "&current=temperature_2m,weather_code" +
                "&daily=weathercode,temperature_2m_max,temperature_2m_min" +
                "&forecast_days=4",
            WeatherForecastUrl.forecast(latitude = 48.85, longitude = 2.35),
        )
    }

    @Test
    fun theQueryHasExactlyTheDocumentedParameters() {
        val query = queryOf(WeatherForecastUrl.forecast(latitude = -33.87, longitude = 151.21))

        assertEquals(
            setOf("latitude", "longitude", "current", "daily", "forecast_days"),
            query.keys,
        )
        assertEquals("-33.87", query["latitude"])
        assertEquals("151.21", query["longitude"])
        assertEquals("4", query["forecast_days"])
    }

    @Test
    @OptIn(ExperimentalSerializationApi::class)
    fun everyRequestedFieldIsOneTheWeatherResponseDecodes() {
        val decodedCurrent = CurrentWeather.serializer().descriptor.elementNames.toSet()
        val decodedDaily = Daily.serializer().descriptor.elementNames.toSet()

        assertEquals(
            setOf("temperature_2m", "weather_code"),
            WeatherForecastUrl.CURRENT_FIELDS.split(",").toSet(),
        )
        assertEquals(
            setOf("weathercode", "temperature_2m_max", "temperature_2m_min"),
            WeatherForecastUrl.DAILY_FIELDS.split(",").toSet(),
        )
        // A requested field the decoder does not know is dropped silently, and a decoded field the
        // request never asks for arrives as an empty list; both render "N/A".
        assertTrue(decodedCurrent.containsAll(WeatherForecastUrl.CURRENT_FIELDS.split(",")))
        assertTrue(decodedDaily.containsAll(WeatherForecastUrl.DAILY_FIELDS.split(",")))
    }

    @Test
    @OptIn(ExperimentalSerializationApi::class)
    fun dailyTimeIsDecodedButNotRequested() {
        val decodedDaily = Daily.serializer().descriptor.elementNames.toList()

        assertTrue(decodedDaily.contains("time"))
        assertEquals(
            decodedDaily - "time",
            WeatherForecastUrl.DAILY_FIELDS.split(","),
        )
    }

    private fun queryOf(url: String): Map<String, String> =
        url.substringAfter("?").split("&").associate { parameter ->
            parameter.substringBefore("=") to parameter.substringAfter("=")
        }
}
