package ovh.litapp.neurhome3.ui.categories

import android.content.Intent
import android.content.pm.LauncherApps
import android.location.Location
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.repositories.NeurhomeRepository
import ovh.litapp.neurhome3.data.models.Tag
import ovh.litapp.neurhome3.data.repositories.TagRepository
import ovh.litapp.neurhome3.data.repositories.FavouritesRepository
import ovh.litapp.neurhome3.ui.NeurhomeViewModel

class CategoryViewModel(
    private val tagRepository: TagRepository,
    neurhomeRepository: NeurhomeRepository,
    favouritesRepository: FavouritesRepository,
    startActivity: (Intent) -> Unit,
    getSSID: () -> String?,
    getPosition: () -> Location?,
    launcherApps: LauncherApps,
    checkPermission: (String) -> Boolean,
) : NeurhomeViewModel(
    neurhomeRepository,
    favouritesRepository,
    startActivity,
    getSSID,
    getPosition,
    launcherApps,
    checkPermission,
) {
    val tags: StateFlow<List<Tag>> = tagRepository.tags.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    fun createTag(name: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            onResult(tagRepository.createTag(name))
        }
    }

    fun applicationsForTag(name: String?): Flow<List<Application>> =
        neurhomeRepository.applicationsForTag(name)
}
