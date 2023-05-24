package ovh.litapp.neurhome3.ui.applications

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.NeurhomeRepository
import ovh.litapp.neurhome3.ui.NeurhomeViewModel

class AllApplicationsViewModel(
    private val neurhomeRepository: NeurhomeRepository,
    packageManager: PackageManager,
    startActivity: (Intent) -> Unit,
    getSSID: () -> String?,
) : NeurhomeViewModel(neurhomeRepository, packageManager, startActivity, getSSID) {

    val uiState: StateFlow<UiState> = neurhomeRepository.apps.map { UiState(it) }.stateIn(
        viewModelScope, started = SharingStarted.WhileSubscribed(), initialValue = UiState()
    )

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    fun import(u: Uri?) {
        coroutineScope.launch(Dispatchers.IO) {
            neurhomeRepository.insertFromDB(u)
        }
    }
}

data class UiState(
    val allApps: List<Application> = listOf(),
)
