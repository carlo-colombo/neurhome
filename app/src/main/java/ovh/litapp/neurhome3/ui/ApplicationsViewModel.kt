package ovh.litapp.neurhome3.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.Integer.min
import java.util.*

private const val TAG = "ApplicationsViewModel"

class ApplicationsViewModel(
    private val packageManager: PackageManager,
    private val startActivity: (Intent) -> Unit,
    val vibrate: () -> Unit
) : ViewModel() {
    private val _uiState = MutableStateFlow(ApplicationsUiState())
    val uiState: StateFlow<ApplicationsUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = ApplicationsUiState(apps = apps())
    }

    fun launch(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            startActivity(intent)
        }
    }

    @Suppress("DEPRECATION")
    private fun apps(): List<Application> {
        Log.d(TAG, "Loading apps")
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        return packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL).map { app ->
            val packageName = app.activityInfo.packageName
            Application(
                label = app.loadLabel(packageManager).toString(),
                packageName = packageName,
                icon = packageManager.getApplicationIcon(packageName)
            )
        }.sortedBy { it.label.lowercase() }
    }

    fun push(s: String) {
        uiState.value.query.add(s)
        updateHomeApps()
        Log.d(TAG, "Query ${uiState.value.query}")
    }

    fun pop() {
        if (uiState.value.query.size > 0) uiState.value.query.removeLast()
        updateHomeApps()
        Log.d(TAG, "Query ${uiState.value.query}")
    }


    fun clearQuery() {
        uiState.value.query.clear()
        uiState.value.homeApps.clear()
        uiState.value.homeApps.addAll(uiState.value.apps.slice( 6))
    }

    private fun updateHomeApps() {
        val r = Regex(
            ".*\\b(my)?" + uiState.value.query.joinToString("") + ".*", RegexOption.IGNORE_CASE
        )

        val filteredApps = uiState.value.apps.filter {
            it.label matches r
        }

        uiState.value.homeApps.clear()
        uiState.value.homeApps.addAll(filteredApps.slice(6))

        Log.d(TAG, "$r: ${uiState.value.homeApps.size}")
    }
}

fun List<Application>.slice(count: Int): List<Application> {
    return this.subList(0, min(count, this.size))
}

data class Application(
    val label: String = "", val packageName: String = "", val icon: Drawable
)

data class ApplicationsUiState(
    val apps: List<Application> = Collections.emptyList(),
    val query: MutableList<String> = mutableStateListOf(),
    val homeApps: MutableList<Application> = mutableStateListOf(),
) {
    constructor(apps: List<Application>) : this(
        apps = apps,
        homeApps = mutableStateListOf(*apps.slice(6).toTypedArray())
    )
}