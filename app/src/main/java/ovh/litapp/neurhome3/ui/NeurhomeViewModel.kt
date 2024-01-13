package ovh.litapp.neurhome3.ui

import android.content.Intent
import android.content.Intent.ACTION_DELETE
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.LauncherApps
import android.location.Location
import android.net.Uri
import androidx.lifecycle.ViewModel
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.NeurhomeRepository

interface INeurhomeViewModel {
    val appActions: AppActions

    data class AppActions(
        val remove: (String) -> Unit = {},
        val launch: (Application?, Boolean) -> Unit = { _, _ -> },
        val toggleVisibility: (String) -> Unit = {},
        val setFavourite: (String, Int) -> Unit = { _, _ -> }
    )
}

abstract class NeurhomeViewModel(
    private val neurhomeRepository: NeurhomeRepository,
    private val startActivity: (Intent) -> Unit,
    private val getSSID: () -> String?,
    private val getPosition: () -> Location?,
    private val launcherApps: LauncherApps,
) : ViewModel(), INeurhomeViewModel {
    open fun launch(launcherActivityInfo: Application?, track: Boolean) {
        launcherActivityInfo?.let { activityInfo ->
            val user = activityInfo.appInfo?.user
            val componentName = activityInfo.appInfo?.componentName
            launcherApps.startMainActivity(componentName, user, null, null)

            if (track && activityInfo.appInfo != null) {
                neurhomeRepository.logLaunch(activityInfo.appInfo, getSSID(), getPosition())
            }
        }
    }

    fun remove(packageName: String) {
        val intent = Intent(ACTION_DELETE)
        intent.flags = FLAG_ACTIVITY_NEW_TASK
        intent.data = Uri.parse("package:$packageName")
        startActivity(intent)
    }

    fun toggleVisibility(packageName: String) {
        neurhomeRepository.toggleVisibility(packageName)
    }

    fun setFavourite(packageName: String, index: Int) =
        neurhomeRepository.setFavourite(packageName, index)

    override val appActions: INeurhomeViewModel.AppActions =
        INeurhomeViewModel.AppActions(
            remove = ::remove,
            launch = ::launch,
            ::toggleVisibility,
            ::setFavourite
        )
}