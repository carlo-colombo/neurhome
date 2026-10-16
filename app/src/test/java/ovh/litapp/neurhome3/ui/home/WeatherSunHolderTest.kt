package ovh.litapp.neurhome3.ui.home

import android.app.Application
import android.location.Location
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ovh.litapp.neurhome3.data.repositories.ClockAlarmRepository
import ovh.litapp.neurhome3.data.weather.CurrentWeather
import ovh.litapp.neurhome3.data.weather.Daily
import ovh.litapp.neurhome3.data.weather.WeatherRepository
import ovh.litapp.neurhome3.data.weather.WeatherResponse
import ovh.litapp.neurhome3.data.weather.WeatherService

private val weatherResponse = WeatherResponse(
    current = CurrentWeather(temperature = 12.3, weatherCode = 0),
    daily = Daily(
        time = listOf("2024-01-01", "2024-01-02", "2024-01-03", "2024-01-04"),
        weatherCode = listOf(0, 1, 2, 3),
        temperatureMax = listOf(15.0, 16.0, 17.0, 18.0),
        temperatureMin = listOf(5.0, 6.0, 7.0, 8.0),
    ),
)

/**
 * Covers [WeatherSunHolder], which owns the Home dashboard's weather and sun panels now that the
 * subsystem has moved out of `HomeViewModel`. The load-bearing behaviour is the in-flight
 * de-duplication: `fetchWeather` and `fetchSun` are re-entrant entry points (`HomeViewModel.init`
 * calls them once and the dashboard refreshes them), and without the `Job.isActive` guard every
 * call would start another network request and another solar computation on top of a running one.
 *
 * Everything runs on `runTest` virtual time. "In flight" is a real suspension point inside a fake
 * service or a fake city lookup, so a second call is observed while the first job is genuinely
 * active instead of after it already finished. The holder and its state collectors belong to
 * `backgroundScope` (they outlive the test body), and `advanceUntilIdle` deliberately stops once
 * only background work is left, so virtual time is drained with `runCurrent` instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WeatherSunHolderTest {
    private val position: Location = Location("test").apply {
        latitude = 48.85
        longitude = 2.35
    }

    /** Positions the holder asked for; a second in-flight job would read this twice. */
    private var positionReads = 0

    @Test
    fun aSecondFetchWhileWeatherIsInFlightDoesNotStartASecondRequest() = runTest {
        val service = GatedWeatherService(gated = true)
        val holder = holder(service = service)

        holder.fetchWeather()
        runCurrent()
        assertEquals(listOf(48.85 to 2.35), service.calls)

        // One request is in flight: the guard has to swallow these calls entirely.
        holder.fetchWeather()
        holder.fetchWeather()
        runCurrent()
        assertEquals(1, service.calls.size)

        service.release()
        runCurrent()
        assertEquals(weatherResponse, holder.weatherUIState.value.weather)
        assertEquals("Testville", holder.weatherUIState.value.city)
        assertFalse(holder.weatherUIState.value.loading)

        // The job finished, so the next fetch is a new request rather than a swallowed one.
        holder.fetchWeather()
        runCurrent()
        assertEquals(2, service.calls.size)
    }

    @Test
    fun aSecondFetchWhileTheSunIsInFlightDoesNotRecomputeTheTimes() = runTest {
        val cityLookup = CompletableDeferred<String?>()
        val holder = holder(getCityName = { cityLookup.await() })
        collectSunState(holder)

        holder.fetchSun()
        runCurrent()
        assertEquals(1, positionReads)

        // The first job is suspended on the city lookup; a second fetch must not start another
        // solar computation on top of it.
        holder.fetchSun()
        holder.fetchSun()
        runCurrent()
        assertEquals(1, positionReads)

        cityLookup.complete("Testville")
        runCurrent()
        val sun = holder.sunUIState.value
        assertEquals("Testville", sun.city)
        assertNotNull(sun.firstEvent)
        assertNotNull(sun.secondEvent)
        assertFalse(sun.loading)

        holder.fetchSun()
        runCurrent()
        assertEquals(2, positionReads)
    }

    @Test
    fun aMissingPositionClearsLoadingWithoutRequestingWeatherOrSun() = runTest {
        val service = GatedWeatherService()
        val holder = holder(service = service, getPosition = { null })
        collectSunState(holder)

        holder.fetchWeather()
        holder.fetchSun()
        runCurrent()

        assertEquals(emptyList<Pair<Double, Double>>(), service.calls)
        assertEquals(0, positionReads)
        assertNull(holder.weatherUIState.value.weather)
        assertFalse(holder.weatherUIState.value.loading)
        assertFalse(holder.sunUIState.value.loading)
        assertNull(holder.sunUIState.value.firstEvent)
    }

    @Test
    fun aFailedWeatherRequestLeavesThePanelEmptyAndTheGuardReleased() = runTest {
        val service = GatedWeatherService().apply { failNextRequest = true }
        val holder = holder(service = service)

        holder.fetchWeather()
        runCurrent()
        assertEquals(1, service.calls.size)
        assertNull(holder.weatherUIState.value.weather)
        assertNull(holder.weatherUIState.value.city)
        assertFalse(holder.weatherUIState.value.loading)

        // The failed job released the guard, so a retry reaches the service again.
        holder.fetchWeather()
        runCurrent()
        assertEquals(2, service.calls.size)
        assertEquals(weatherResponse, holder.weatherUIState.value.weather)
        assertFalse(holder.weatherUIState.value.loading)
    }

    private fun TestScope.holder(
        service: WeatherService = GatedWeatherService(),
        getPosition: () -> Location? = { position.also { positionReads++ } },
        getCityName: suspend (Location) -> String? = { "Testville" },
    ): WeatherSunHolder = WeatherSunHolder(
        scope = backgroundScope,
        weatherRepository = WeatherRepository(service),
        clockAlarmRepository = ClockAlarmRepository(RuntimeEnvironment.getApplication()),
        getPosition = getPosition,
        getCityName = getCityName,
    )

    /**
     * [WeatherSunHolder.sunUIState] is a `WhileSubscribed` `stateIn`, so it keeps reporting its
     * initial value until something collects it. The collector outlives the test body, so it
     * belongs to the background scope.
     */
    private fun TestScope.collectSunState(holder: WeatherSunHolder) {
        backgroundScope.launch { holder.sunUIState.collect { } }
    }

    private class GatedWeatherService(gated: Boolean = false) : WeatherService {
        val calls = mutableListOf<Pair<Double, Double>>()

        /** When gated, [getWeather] stays suspended until the test calls [release]. */
        private val gate: CompletableDeferred<Unit>? =
            if (gated) CompletableDeferred() else null
        var failNextRequest = false

        fun release() = gate?.complete(Unit)

        override suspend fun getWeather(latitude: Double, longitude: Double): WeatherResponse {
            calls += latitude to longitude
            gate?.await()
            if (failNextRequest) {
                failNextRequest = false
                throw IllegalStateException("weather service unavailable")
            }
            return weatherResponse
        }
    }
}
