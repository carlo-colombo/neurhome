package ovh.litapp.neurhome3.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import ovh.litapp.neurhome3.data.repositories.NeurhomeRepository
import ovh.litapp.neurhome3.data.repositories.SettingsRepository


interface ISettingsViewModel {
    val uiState: StateFlow<Settings>
    fun toggleWifi()
    fun toggleLogPosition()
    fun exportDatabase(context: Context)
    fun toggleShowCalendar()
    fun toggleShowStarredContacts()
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val neurhomeRepository: NeurhomeRepository
) : ViewModel(), ISettingsViewModel {
    override val uiState: StateFlow<Settings> = combine(
        settingsRepository.wifiLogging,
        settingsRepository.positionLogging,
        settingsRepository.showCalendar,
        settingsRepository.showStarredContacts
    ) { wifi, position, calendar, starredContacts ->
        Settings(
            logWiFi = wifi,
            logPosition = position,
            showCalendar = calendar,
            starredContacts = starredContacts
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = Settings()
    )

    override fun toggleWifi() {
        settingsRepository.toggleWifiLogging()
    }

    override fun toggleLogPosition() {
        settingsRepository.togglePositionLogging()
    }

    override fun exportDatabase(context: Context) {
        neurhomeRepository.exportDatabase(context)
    }

    override fun toggleShowCalendar() {
        settingsRepository.toggleShowCalendar()
    }

    override fun toggleShowStarredContacts() {
        settingsRepository.toggleShowStarredContacts()
    }
}

data class Settings(
    val logWiFi: Boolean = false,
    val logPosition: Boolean = false,
    val showCalendar: Boolean = false,
    val starredContacts: Boolean = false
)