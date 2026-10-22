package ovh.litapp.neurhome3.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import ovh.litapp.neurhome3.data.repositories.ContactCandidates
import ovh.litapp.neurhome3.data.repositories.HomeAppSelection
import ovh.litapp.neurhome3.data.repositories.NeurhomeRepository
import ovh.litapp.neurhome3.data.repositories.SettingsRepository


interface ISettingsViewModel {
    val uiState: StateFlow<SettingsUiState>
    fun setWifiLogging(enabled: Boolean, permissionGranted: Boolean)
    fun setPositionLogging(enabled: Boolean, permissionGranted: Boolean)
    fun exportDatabase(context: Context)
    fun setShowCalendar(enabled: Boolean, permissionGranted: Boolean)
    fun setContactCandidates(candidates: ContactCandidates, permissionGranted: Boolean)
    fun toggleShowAlternativeTime()
    fun saveTimeZone(timeZone: String)
    fun setHomeAppSelection(selection: HomeAppSelection)
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val neurhomeRepository: NeurhomeRepository
) : ViewModel(), ISettingsViewModel {
    override val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.wifiLogging,
        settingsRepository.positionLogging,
        settingsRepository.showCalendar,
        settingsRepository.contactCandidates,
        settingsRepository.showAlternativeTime
    ) { wifi, position, calendar, contactCandidates, showAlternativeTime ->
        SettingsUiState(
            logWiFi = wifi,
            logPosition = position,
            showCalendar = calendar,
            contactCandidates = contactCandidates,
            showAlternativeTime = showAlternativeTime,
        )
    }.combine(settingsRepository.alternativeTimeZone) { settings, timeZone ->
        settings.copy(alternativeTimeZone = timeZone)
    }
        .combine(settingsRepository.homeAppSelection) { settings, selection ->
            settings.copy(homeAppSelection = selection)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = SettingsUiState()
        )

    override fun setWifiLogging(enabled: Boolean, permissionGranted: Boolean) {
        settingsRepository.setWifiLogging(enabled, permissionGranted)
    }

    override fun setPositionLogging(enabled: Boolean, permissionGranted: Boolean) {
        settingsRepository.setPositionLogging(enabled, permissionGranted)
    }

    override fun exportDatabase(context: Context) {
        neurhomeRepository.exportDatabase(context)
    }

    override fun setShowCalendar(enabled: Boolean, permissionGranted: Boolean) {
        settingsRepository.setShowCalendar(enabled, permissionGranted)
    }

    override fun setContactCandidates(candidates: ContactCandidates, permissionGranted: Boolean) {
        settingsRepository.setContactCandidates(candidates, permissionGranted)
    }

    override fun toggleShowAlternativeTime() {
        settingsRepository.toggleShowAlternativeTime()
    }

    override fun saveTimeZone(timeZone: String) {
        settingsRepository.setAlternativeTimeZone(timeZone)
    }

    override fun setHomeAppSelection(selection: HomeAppSelection) {
        settingsRepository.setHomeAppSelection(selection)
    }
}

data class SettingsUiState(
    val logWiFi: Boolean = false,
    val logPosition: Boolean = false,
    val showCalendar: Boolean = false,
    val contactCandidates: ContactCandidates = ContactCandidates.NONE,
    val showAlternativeTime: Boolean = false,
    val alternativeTimeZone: String = "",
    val homeAppSelection: HomeAppSelection = HomeAppSelection.CLASSIC
)