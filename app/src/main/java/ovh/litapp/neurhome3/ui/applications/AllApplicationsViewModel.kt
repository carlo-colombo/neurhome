package ovh.litapp.neurhome3.ui.applications

import android.content.Intent
import android.content.pm.LauncherApps
import android.location.Location
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.repositories.FavouritesRepository
import ovh.litapp.neurhome3.data.repositories.NeurhomeRepository
import ovh.litapp.neurhome3.data.repositories.TagRepository
import ovh.litapp.neurhome3.ui.NeurhomeViewModel

class AllApplicationsViewModel(
    neurhomeRepository: NeurhomeRepository,
    favouritesRepository: FavouritesRepository,
    startActivity: (Intent) -> Unit,
    getSSID: () -> String?,
    getPosition: () -> Location?,
    launcherApps: LauncherApps,
    checkPermission: (String) -> Boolean,
    tagRepository: TagRepository,
) : NeurhomeViewModel(
    neurhomeRepository,
    favouritesRepository,
    startActivity,
    getSSID,
    getPosition,
    launcherApps,
    checkPermission
) {
    val uiState: StateFlow<UiState> = combine(neurhomeRepository.applicationAndContacts, tagRepository.tags) { apps, tags ->
        UiState(apps, tags.map { it.name })
    }.stateIn(
        viewModelScope, started = SharingStarted.WhileSubscribed(), initialValue = UiState()
    )
}

data class UiState(
    val allApps: List<Application> = listOf(),
    val tags: List<String> = emptyList(),
)
