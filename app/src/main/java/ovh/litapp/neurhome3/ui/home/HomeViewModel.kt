package ovh.litapp.neurhome3.ui.home

import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.NeurhomeRepository
import ovh.litapp.neurhome3.ui.NeurhomeViewModel

private const val TAG = "HomeViewModel"

class HomeViewModel(
    neurhomeRepository: NeurhomeRepository,
    packageManager: PackageManager,
    startActivity: (Intent) -> Unit,
) : NeurhomeViewModel(neurhomeRepository, packageManager, startActivity) {

    private val query = MutableStateFlow<List<String>>(listOf())

    val homeUiState: StateFlow<HomeUiState> =
        combine(
            neurhomeRepository.topApps,
            neurhomeRepository.apps,
            query
        ) { topApps, allApps, query ->
            if (query.isEmpty()) {
                HomeUiState(allApps = allApps, homeApps = topApps, query = query)
            } else {
                Log.d(TAG, "Query: $query")
                val r = Regex(
                    ".*\\b(my)?" + query.joinToString("") + ".*", RegexOption.IGNORE_CASE
                )

                HomeUiState(
                    allApps = allApps,
                    homeApps = allApps.filter { r matches it.label }.take(6),
                    query = query
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = HomeUiState()
        )

    fun push(s: String) {
        query.update { it + s }
    }

    fun pop() {
        query.update {
            if (it.isNotEmpty())
                it.take(it.size - 1)
            else it
        }
    }

    fun clearQuery() {
        query.update { listOf() }
    }
}

data class HomeUiState(
    val allApps: List<Application> = listOf(),
    val query: List<String> = listOf(),
    var homeApps: List<Application> = listOf(),
)