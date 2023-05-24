package ovh.litapp.neurhome3.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import ovh.litapp.neurhome3.data.SettingsRepository

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    val uiState: StateFlow<Settings> =
        combine(
            settingsRepository.wifiLogging,
            settingsRepository.positionLogging
        ) { wifi, position ->
            Settings(logWiFi = wifi, logPosition = position)
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
}

data class Settings(val logWiFi: Boolean = false, val logPosition: Boolean = false)