package ovh.litapp.neurhome3.ui

import android.content.Intent
import android.content.Intent.ACTION_DELETE
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.LauncherApps
import android.location.Location
import android.net.Uri
import androidx.lifecycle.ViewModel
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.repositories.NeurhomeRepository

interface INeurhomeViewModel {
    val appActions: AppActions

    data class AppActions(
        val remove: (Application) -> Unit = {},
        val launch: (Application?, Boolean) -> Unit = { _, _ -> },
        val toggleVisibility: (Application) -> Unit = {},
        val setFavourite: (Application, Int) -> Unit = { _, _ -> }
    )
}

abstract class NeurhomeViewModel(
    private val neurhomeRepository: NeurhomeRepository,
    private val startActivity: (Intent) -> Unit,
    private val getSSID: () -> String?,
    private val getPosition: () -> Location?,
    private val launcherApps: LauncherApps,
) : ViewModel(), INeurhomeViewModel {
    open fun launch(launcherActivityInfo: Application?, track: Boolean, query: String? = null) {
        launcherActivityInfo?.let { activityInfo ->
            val user = activityInfo.appInfo?.user
            val componentName = activityInfo.appInfo?.componentName
            launcherApps.startMainActivity(componentName, user, null, null)

            if (track && activityInfo.appInfo != null) {
                neurhomeRepository.logLaunch(activityInfo.appInfo, getSSID(), getPosition(), query)
            }
        }
    }

    private fun remove(application: Application) {
        val intent = Intent(ACTION_DELETE)
        intent.flags = FLAG_ACTIVITY_NEW_TASK
        intent.data = Uri.parse("package:${application.packageName}")
        startActivity(intent)
    }

    private fun toggleVisibility(application: Application) =
        neurhomeRepository.toggleVisibility(application)

    private fun setFavourite(application: Application, index: Int) =
        neurhomeRepository.setFavourite(application, index)

    override val appActions: INeurhomeViewModel.AppActions =
        INeurhomeViewModel.AppActions(
            ::remove, ::launch, ::toggleVisibility, ::setFavourite
        )
}