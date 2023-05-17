package ovh.litapp.neurhome3.ui.home

import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ovh.litapp.neurhome3.data.NeurhomeRepository
import ovh.litapp.neurhome3.ui.Application
import java.lang.Integer.min

private const val TAG = "HomeViewModel"

class HomeViewModel(
    private val neurhomeRepository: NeurhomeRepository,
    private val packageManager: PackageManager,
    private val startActivity: (Intent) -> Unit,
) : ViewModel(
) {
    val homeUiState: StateFlow<HomeUiState> =
        neurhomeRepository.topApps.map { HomeUiState(it) }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState()
        )

    fun launch(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(
            packageName
        )
        if (intent != null) {
            startActivity(intent)
            neurhomeRepository.logLaunch(packageName)
        }
    }

    fun push(s: String) {
        homeUiState.value.query.add(s)
        updateHomeApps()
        Log.d(TAG, "Query ${homeUiState.value.query}")
    }

    fun pop() {
        if (homeUiState.value.query.size > 0) homeUiState.value.query.removeLast()
        updateHomeApps()
        Log.d(TAG, "Query ${homeUiState.value.query}")
    }

    private fun updateHomeApps() {
        val r = Regex(
            ".*\\b(my)?" + homeUiState.value.query.joinToString("") + ".*", RegexOption.IGNORE_CASE
        )

        val filteredApps = homeUiState.value.apps.filter {
            it.label matches r
        }

        homeUiState.value.homeApps.clear()
        homeUiState.value.homeApps.addAll(filteredApps.slice(6))

        Log.d(TAG, "$r: ${homeUiState.value.homeApps.size}")
    }

    fun clearQuery() {
        homeUiState.value.query.clear()
        homeUiState.value.homeApps.clear()
        homeUiState.value.homeApps.addAll(homeUiState.value.apps.slice(6))
    }
}

fun List<Application>.slice(count: Int): List<Application> {
    return this.subList(0, min(count, this.size))
}

data class HomeUiState(
    val apps: List<Application> = listOf(),
    val query: MutableList<String> = mutableStateListOf(),
    val homeApps: MutableList<Application> = mutableStateListOf(),
)
