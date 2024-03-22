package ovh.litapp.neurhome3.ui.applications

import android.content.Intent
import android.content.pm.LauncherApps
import android.location.Location
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.repositories.NeurhomeRepository
import ovh.litapp.neurhome3.ui.NeurhomeViewModel

class AllApplicationsViewModel(
    neurhomeRepository: NeurhomeRepository,
    startActivity: (Intent) -> Unit,
    getSSID: () -> String?,
    getPosition: () -> Location?,
    launcherApps: LauncherApps,
) : NeurhomeViewModel(
    neurhomeRepository, startActivity, getSSID, getPosition, launcherApps
) {
    val uiState: StateFlow<UiState> = neurhomeRepository.apps.map { UiState(it) }.stateIn(
        viewModelScope, started = SharingStarted.WhileSubscribed(), initialValue = UiState()
    )
}

data class UiState(
    val allApps: List<Application> = listOf(),
)
