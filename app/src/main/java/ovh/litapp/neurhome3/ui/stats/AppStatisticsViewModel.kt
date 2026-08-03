package ovh.litapp.neurhome3.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.repositories.NeurhomeRepository
import java.time.LocalTime
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class AppStatisticsViewModel(
    private val neurhomeRepository: NeurhomeRepository
) : ViewModel() {
    private val _dayOfWeek = MutableStateFlow(Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1)
    private val _time = MutableStateFlow(LocalTime.now())
    private val _wifi = MutableStateFlow<String?>(null)

    val availableSSIDs: StateFlow<List<String>> = neurhomeRepository.getUniqueSSIDs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<AppStatisticsUiState> = combine(
        _dayOfWeek, _time, _wifi
    ) { day, time, wifi ->
        Triple(day, time, wifi)
    }.flatMapLatest { (day, time, wifi) ->
        val minuteOfDay = time.hour * 60 + time.minute
        neurhomeRepository.getSimulatedTopApps(day, minuteOfDay, wifi).combine(availableSSIDs) { apps, ssids ->
            AppStatisticsUiState(
                usedApps = apps,
                selectedDay = day,
                selectedTime = time,
                selectedWifi = wifi,
                availableSSIDs = ssids
            )
        }
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
    val selectedDay: Int = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1,
    val selectedTime: LocalTime = LocalTime.now(),
    val selectedWifi: String? = null,
    val availableSSIDs: List<String> = emptyList()
)
