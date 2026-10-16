package ovh.litapp.neurhome3.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.repositories.NeurhomeRepository
import java.time.LocalTime
import java.util.Calendar

/**
 * How long the statistics screen waits after the last time-slider move before it re-runs the
 * ranking query: long enough that one drag collapses into a single query, short enough that letting
 * go of the slider still feels immediate.
 */
internal const val SLIDER_QUERY_DEBOUNCE_MILLIS = 150L

/**
 * Debounces the time slider's selections so a drag produces one ranking query when it settles
 * instead of one multi-CTE query per tick. Only the query input is debounced; the displayed
 * selection is not.
 */
@OptIn(FlowPreview::class)
internal fun Flow<LocalTime>.debounceSliderQueries(): Flow<LocalTime> =
    debounce(SLIDER_QUERY_DEBOUNCE_MILLIS)

/**
 * Index of today in the day selector: `Calendar.DAY_OF_WEEK` is 1-based from Sunday while the
 * selector is 0-based. Shared by the ViewModel's initial state and [AppStatisticsUiState]'s
 * defaults so the two cannot disagree about which day "today" is.
 */
private fun todayDayOfWeekIndex(): Int = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1

/**
 * State of the statistics screen's simulated ranking. It deliberately stays a plain [ViewModel]
 * instead of the shared `NeurhomeViewModel`: this screen only reads the ranking, so the launching,
 * favourites and logging graph that base class carries would all go unused here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppStatisticsViewModel(
    private val neurhomeRepository: NeurhomeRepository
) : ViewModel() {
    private val _dayOfWeek = MutableStateFlow(todayDayOfWeekIndex())
    private val _time = MutableStateFlow(LocalTime.now())
    private val _wifi = MutableStateFlow<String?>(null)

    val availableSSIDs: StateFlow<List<String>> = neurhomeRepository.getUniqueSSIDs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * The simulated ranking for the debounced slider position. Only the time is debounced: a day or
     * Wi-Fi selection is a single discrete change and is queried right away.
     */
    private val selectedApps = combine(
        _dayOfWeek, _time.debounceSliderQueries(), _wifi
    ) { day, time, wifi ->
        Triple(day, time, wifi)
    }.flatMapLatest { (day, time, wifi) ->
        val minuteOfDay = time.hour * 60 + time.minute
        neurhomeRepository.getSimulatedTopApps(day, minuteOfDay, wifi)
    }

    val uiState: StateFlow<AppStatisticsUiState> = combine(
        _dayOfWeek, _time, _wifi, selectedApps, availableSSIDs
    ) { day, time, wifi, apps, ssids ->
        AppStatisticsUiState(
            usedApps = apps,
            selectedDay = day,
            selectedTime = time,
            selectedWifi = wifi,
            availableSSIDs = ssids
        )
    }.stateIn(
        viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppStatisticsUiState()
    )

    fun onDaySelected(day: Int) {
        _dayOfWeek.value = day
    }

    fun onTimeSelected(time: LocalTime) {
        _time.value = time
    }

    fun onWifiSelected(wifi: String?) {
        _wifi.value = wifi
    }
}

data class AppStatisticsUiState(
    val usedApps: List<Application> = emptyList(),
    val selectedDay: Int = todayDayOfWeekIndex(),
    val selectedTime: LocalTime = LocalTime.now(),
    val selectedWifi: String? = null,
    val availableSSIDs: List<String> = emptyList()
)
