package ovh.litapp.neurhome3.data.weather

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val weatherResponse = WeatherResponse(
    current = CurrentWeather(temperature = 12.3, weatherCode = 0),
    daily = Daily(
        time = listOf("2024-01-01", "2024-01-02", "2024-01-03", "2024-01-04"),
        weatherCode = listOf(0, 1, 2, 3),
        temperatureMax = listOf(15.0, 16.0, 17.0, 18.0),
        temperatureMin = listOf(5.0, 6.0, 7.0, 8.0)
    )
)

/**
 * Pins [WeatherRepository]'s contract towards the app-scoped weather service chosen by Task 12:
 * requests go through the service instance the repository was constructed with, and a failing
 * request surfaces as `Result.failure` without disposing that service. The process-wide
 * singleton wiring itself lives in `NeurhomeApplication` and needs a `Context`, so it is not
 * exercised here.
 */
class WeatherServiceLifecycleTest {
    private class FakeWeatherService : WeatherService {
        val requestedCoordinates = mutableListOf<Pair<Double, Double>>()
        var failNextRequest = false

        override suspend fun getWeather(latitude: Double, longitude: Double): WeatherResponse {
            requestedCoordinates += latitude to longitude
            if (failNextRequest) {
                failNextRequest = false
                throw IllegalStateException("weather service unavailable")
            }
            return weatherResponse
        }
    }

    @Test
    fun successfulRequestForwardsCoordinatesAndReturnsTheResponse() = runBlocking {
        val service = FakeWeatherService()
        val repository = WeatherRepository(service)

        val result = repository.getWeather(48.85, 2.35)

        assertEquals(weatherResponse, result.getOrNull())
        assertEquals(listOf(48.85 to 2.35), service.requestedCoordinates)
    }

    @Test
    fun serviceFailureBecomesAFailureResultAndDoesNotEscape() = runBlocking {
        val service = FakeWeatherService().apply { failNextRequest = true }
        val repository = WeatherRepository(service)

        val result = repository.getWeather(48.85, 2.35)

        assertTrue(result.isFailure)
        assertEquals("weather service unavailable", result.exceptionOrNull()?.message)
    }

    @Test
    fun failedRequestLeavesTheSameServiceUsableForTheNextRequest() = runBlocking {
        val service = FakeWeatherService().apply { failNextRequest = true }
        val repository = WeatherRepository(service)

        val failed = repository.getWeather(48.85, 2.35)
        val succeeded = repository.getWeather(48.85, 2.35)

        assertTrue(failed.isFailure)
        assertEquals(weatherResponse, succeeded.getOrNull())
        // Both requests reached the injected service, so the failure did not drop or replace
        // the client the repository was given.
        assertEquals(2, service.requestedCoordinates.size)
    }
}
