package ovh.litapp.neurhome2.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

private const val TAG = "ApplicationsViewModel"

class ApplicationsViewModel(
    private val packageManager: PackageManager,
    private val startActivity: (Intent) -> Unit
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

        return packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { app ->
                val packageName = app.activityInfo.packageName
                Application(
                    label = app.loadLabel(packageManager).toString(),
                    packageName = packageName,
                    icon = packageManager.getApplicationIcon(packageName)
                )
            }
            .sortedBy { it.label.lowercase() }
    }
}

data class Application(
    val label: String = "", val packageName: String = "", val icon: Drawable
)

data class ApplicationsUiState(
    val apps: List<Application> = Collections.emptyList()
)