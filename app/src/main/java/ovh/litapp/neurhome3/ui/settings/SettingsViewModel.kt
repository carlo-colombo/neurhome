package ovh.litapp.neurhome3.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import ovh.litapp.neurhome3.data.NeurhomeRepository
import ovh.litapp.neurhome3.data.SettingsRepository

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val neurhomeRepository: NeurhomeRepository
) : ViewModel() {
    val uiState: StateFlow<Settings> =
        combine(
            settingsRepository.wifiLogging,
            settingsRepository.positionLogging,
            settingsRepository.showCalendar
        ) { wifi, position, calendar ->
            Settings(logWiFi = wifi, logPosition = position, showCalendar = calendar)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = Settings()
        )

    fun toggleWifi() {
        settingsRepository.toggleWifiLogging()
    }

    fun toggleLogPosition() {
        settingsRepository.togglePositionLogging()
    }

    fun exportDatabase(context: Context) {
        neurhomeRepository.exportDatabase(context)
    }

    fun toggleShowCalendar() {
        settingsRepository.toggleShowCalendar()
    }
}

data class Settings(
    val logWiFi: Boolean = false,
    val logPosition: Boolean = false,
    val showCalendar: Boolean = false
)