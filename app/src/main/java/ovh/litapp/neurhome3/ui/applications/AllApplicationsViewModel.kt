package ovh.litapp.neurhome3.ui.applications

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.repositories.TagRepository
import ovh.litapp.neurhome3.ui.NeurhomeViewModel
import ovh.litapp.neurhome3.ui.NeurhomeViewModelDependencies

class AllApplicationsViewModel(
    dependencies: NeurhomeViewModelDependencies,
    tagRepository: TagRepository,
) : NeurhomeViewModel(dependencies) {
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
