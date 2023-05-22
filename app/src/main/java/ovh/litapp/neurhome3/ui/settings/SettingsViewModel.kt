package ovh.litapp.neurhome3.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ovh.litapp.neurhome3.data.SettingsRepository

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    val uiState: StateFlow<Settings> = settingsRepository.wifiLoggingSetting.map {
        Settings(logWiFi = it)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = Settings()
    )

    fun toggleWifi() {
        settingsRepository.toggleWifiLogging()
    }
}

data class Settings(val logWiFi: Boolean = false)