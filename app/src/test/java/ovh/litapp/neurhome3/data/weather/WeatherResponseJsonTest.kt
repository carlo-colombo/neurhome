package ovh.litapp.neurhome3.data.weather

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val json = Json { ignoreUnknownKeys = true }

/**
 * Captured from `api.open-meteo.com/v1/forecast` for the URL [WeatherServiceImpl] builds.
 * Note the asymmetry the API uses: the current block spells the key `weather_code`, while the
 * daily block spells it `weathercode`. [WeatherServiceImpl] swallows decode failures through
 * [WeatherRepository]'s `Result`, so a wrong `@SerialName` would silently render "N/A" forever;
 * these tests fail instead.
 */
private const val CAPTURED_PAYLOAD = """
{
  "latitude": 48.85,
  "longitude": 2.35,
  "generationtime_ms": 0.123,
  "utc_offset_seconds": 7200,
  "timezone": "Europe/Paris",
  "timezone_abbreviation": "CEST",
  "elevation": 42.0,
  "current_units": { "time": "iso8601", "interval": "seconds", "temperature_2m": "°C", "weather_code": "wmo code" },
  "current": { "time": "2024-06-21T13:00", "interval": 900, "temperature_2m": 21.4, "weather_code": 3 },
  "daily_units": { "time": "iso8601", "weathercode": "wmo code", "temperature_2m_max": "°C", "temperature_2m_min": "°C" },
  "daily": {
    "time": ["2024-06-21", "2024-06-22", "2024-06-23", "2024-06-24"],
    "weathercode": [3, 61, 0, 45],
    "temperature_2m_max": [23.1, 19.8, 25.4, 22.0],
    "temperature_2m_min": [14.2, 12.9, 15.0, 13.3]
  }
}
"""

class WeatherResponseJsonTest {
    @Test
    fun capturedOpenMeteoPayloadDecodesIntoTheSerialNamesTheServiceRequests() {
        val response = json.decodeFromString<WeatherResponse>(CAPTURED_PAYLOAD)

        assertEquals(21.4, response.current.temperature, 0.0)
        assertEquals(3, response.current.weatherCode)
        assertEquals(
            listOf("2024-06-21", "2024-06-22", "2024-06-23", "2024-06-24"),
            response.daily.time
        )
        assertEquals(listOf(3, 61, 0, 45), response.daily.weatherCode)
        assertEquals(listOf(23.1, 19.8, 25.4, 22.0), response.daily.temperatureMax)
        assertEquals(listOf(14.2, 12.9, 15.0, 13.3), response.daily.temperatureMin)
        assertEquals(listOf(3, 61, 0, 45), forecastDays(response.daily).map { it.weatherCode })
    }

    @Test
    fun currentAndDailyUseDifferentWeatherCodeKeys() {
        // The daily block must keep `weathercode`; reusing the current block's `weather_code`
        // key would leave the daily lists empty and the weather row blank.
        val withCurrentSpelling = CAPTURED_PAYLOAD
            .replace("\"weathercode\": [3, 61, 0, 45]", "\"weather_code\": [3, 61, 0, 45]")

        try {
            json.decodeFromString<WeatherResponse>(withCurrentSpelling)
            throw AssertionError("daily `weather_code` must not satisfy the `weathercode` field")
        } catch (expected: SerializationException) {
            assertTrue(expected.message.orEmpty().contains("weathercode"))
        }
    }

    @Test
    fun partialPayloadsDecodeToTheirDayCount() {
        assertEquals(1, forecastDays(json.decodeFromString<WeatherResponse>(payloadOf(1)).daily).size)
        assertEquals(3, forecastDays(json.decodeFromString<WeatherResponse>(payloadOf(3)).daily).size)
    }

    @Test
    fun decodeFailureSurfacesAsAResultFailureInsteadOfEscaping() = runBlocking {
        // A response whose daily weather codes use the current block's key fails to decode and
        // must reach the caller as Result.failure, never as a crash.
        val service = object : WeatherService {
            override suspend fun getWeather(latitude: Double, longitude: Double): WeatherResponse =
                json.decodeFromString(
                    payloadOf(3).replace("\"weathercode\": [0, 1, 2]", "\"weather_code\": [0, 1, 2]")
                )
        }

        val result = WeatherRepository(service).getWeather(48.85, 2.35)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SerializationException)
    }

    private fun payloadOf(days: Int): String {
        val dates = List(days) { "2024-01-0${it + 1}" }.joinToString(", ") { "\"$it\"" }
        val codes = List(days) { it }.joinToString(", ")
        val max = List(days) { 10.0 + it }.joinToString(", ")
        val min = List(days) { it }.joinToString(", ")
        return """
        {
          "current": { "temperature_2m": 12.3, "weather_code": 0 },
          "daily": {
            "time": [$dates],
            "weathercode": [$codes],
            "temperature_2m_max": [$max],
            "temperature_2m_min": [$min]
          }
        }
        """
    }
}
