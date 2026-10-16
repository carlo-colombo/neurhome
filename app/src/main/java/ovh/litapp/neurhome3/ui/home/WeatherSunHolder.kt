package ovh.litapp.neurhome3.ui.home

import android.location.Location
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ovh.litapp.neurhome3.data.repositories.ClockAlarmRepository
import ovh.litapp.neurhome3.data.solar.SolarCalculator
import ovh.litapp.neurhome3.data.solar.SolarEvent
import ovh.litapp.neurhome3.data.solar.SolarTimes
import ovh.litapp.neurhome3.data.weather.WeatherRepository
import ovh.litapp.neurhome3.data.weather.WeatherResponse
import java.time.LocalDate
import java.time.ZoneId

/**
 * Owns the weather and sun panels of the Home dashboard: their UI state, their fetch jobs and the
 * solar cache. It is a plain holder rather than a ViewModel because it shares [scope] and lifecycle
 * with `HomeViewModel`; keeping it separate leaves the Home state tower unaware of how each panel
 * loads.
 */
internal class WeatherSunHolder(
    private val scope: CoroutineScope,
    private val weatherRepository: WeatherRepository,
    clockAlarmRepository: ClockAlarmRepository,
    private val getPosition: () -> Location?,
    private val getCityName: suspend (Location) -> String?,
) {
    private val weatherState = MutableStateFlow(WeatherUIState())
    private val sunLoadingState = MutableStateFlow(true)
    private val cachedSolarData = MutableStateFlow<CachedSolarData?>(null)

    val weatherUIState: StateFlow<WeatherUIState> = weatherState

    val sunUIState: StateFlow<SunUIState> = combine(
        cachedSolarData,
        clockAlarmRepository.time,
        sunLoadingState
    ) { solarData, now, loading ->
        if (solarData == null) {
            SunUIState(loading = loading)
        } else {
            val nowTime = now.toLocalTime()
            val today = solarData.todayTimes
            val tomorrow = solarData.tomorrowTimes

            val todaySunrise = today.toSunriseEvent("Today")
            val todaySunset = today.toSunsetEvent("Today")
            val tomorrowSunrise = tomorrow.toSunriseEvent("Tomorrow")
            val tomorrowSunset = tomorrow.toSunsetEvent("Tomorrow")

            val morningEnd = todaySunrise.transitionTime
            val eveningEnd = todaySunset.transitionTime

            val (firstEvent, secondEvent) = when {
                morningEnd != null && nowTime.isBefore(morningEnd) -> todaySunrise to todaySunset
                eveningEnd != null && nowTime.isBefore(eveningEnd) -> todaySunset to tomorrowSunrise
                else -> tomorrowSunrise to tomorrowSunset
            }

            SunUIState(
                firstEvent = firstEvent,
                secondEvent = secondEvent,
                city = solarData.city,
                loading = loading
            )
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = SunUIState()
    )

    private var fetchWeatherJob: Job? = null

    fun fetchWeather() {
        if (fetchWeatherJob?.isActive == true) return
        fetchWeatherJob = scope.launch {
            if (weatherState.value.weather == null) {
                weatherState.update {
                    it.copy(
                        loading = true
                    )
                }
            }
            val location = getPosition()
            if (location == null) {
                weatherState.update {
                    it.copy(
                        loading = false
                    )
                }
                return@launch
            }

            val result = weatherRepository.getWeather(location.latitude, location.longitude)
            val city = getCityName(location)
            result.onSuccess { weatherResponse ->
                weatherState.update {
                    it.copy(
                        weather = weatherResponse,
                        city = city,
                        loading = false
                    )
                }
            }.onFailure {
                weatherState.update {
                    it.copy(
                        loading = false
                    )
                }
            }
        }
    }

    private var fetchSunJob: Job? = null

    fun fetchSun() {
        if (fetchSunJob?.isActive == true) return
        fetchSunJob = scope.launch {
            val currentDate = LocalDate.now()
            val currentCache = cachedSolarData.value

            if (currentCache == null) {
                sunLoadingState.value = true
            }

            val location = getPosition()
            if (location == null) {
                sunLoadingState.value = false
                return@launch
            }

            val zoneId = ZoneId.systemDefault()
            val todayTimes = SolarCalculator.calculateSolarTimes(
                latitude = location.latitude,
                longitude = location.longitude,
                date = currentDate,
                zoneId = zoneId
            )
            val tomorrowTimes = SolarCalculator.calculateSolarTimes(
                latitude = location.latitude,
                longitude = location.longitude,
                date = currentDate.plusDays(1),
                zoneId = zoneId
            )
            val city = getCityName(location)

            cachedSolarData.value = CachedSolarData(
                date = currentDate,
                todayTimes = todayTimes,
                tomorrowTimes = tomorrowTimes,
                city = city
            )
            sunLoadingState.value = false
        }
    }

    private data class CachedSolarData(
        val date: LocalDate,
        val todayTimes: SolarTimes,
        val tomorrowTimes: SolarTimes,
        val city: String?
    )
}

data class WeatherUIState(
    val weather: WeatherResponse? = null,
    val city: String? = null,
    val loading: Boolean = true
)

data class SunUIState(
    val firstEvent: SolarEvent? = null,
    val secondEvent: SolarEvent? = null,
    val city: String? = null,
    val loading: Boolean = true
)
