package ovh.litapp.neurhome3.ui

import android.Manifest
import android.content.Intent
import android.content.Intent.ACTION_DELETE
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.location.Location
import android.net.Uri
import androidx.lifecycle.ViewModel
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.repositories.FavouritesRepository
import ovh.litapp.neurhome3.data.repositories.NeurhomeRepository

interface INeurhomeViewModel {
    val appActions: AppActions

    data class AppActions(
        val remove: (Application) -> Unit = {},
        val launch: (Application?, track: Boolean) -> Unit = { _, _ -> },
        val toggleVisibility: (Application) -> Unit = {},
        val setFavourite: (Application, Int) -> Unit = { _, _ -> }
    )
}

abstract class NeurhomeViewModel(
    private val neurhomeRepository: NeurhomeRepository,
    private val favouritesRepository: FavouritesRepository,
    private val startActivity: (Intent) -> Unit,
    private val getSSID: () -> String?,
    private val getPosition: () -> Location?,
    private val launcherApps: LauncherApps,
    private val checkPermission: (String) -> Boolean
) : ViewModel(), INeurhomeViewModel {
    open fun launch(application: Application?, track: Boolean, query: String? = null) {
        application?.let {
            if (application.appInfo != null) {
                val appInfo: LauncherActivityInfo = application.appInfo
                val user = appInfo.user
                val componentName = appInfo.componentName
                launcherApps.startMainActivity(componentName, user, null, null)

                if (track) {
                    neurhomeRepository.logLaunch(
                        appInfo.activityInfo.packageName,
                        appInfo.user.hashCode(),
                        getSSID(),
                        getPosition(),
                        query
                    )
                }
            } else if (application.intent != null && checkPermission(Manifest.permission.CALL_PHONE)) {
                val intent = application.intent
                intent.flags = FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                if (track) {
                    neurhomeRepository.logLaunch(
                        intent.data.toString(),
                        launcherApps.profiles[0].hashCode(),
                        getSSID(),
                        getPosition(),
                        query
                    )
                }
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
        favouritesRepository.setFavourite(application, index)

    override val appActions: INeurhomeViewModel.AppActions =
        INeurhomeViewModel.AppActions(
            ::remove, ::launch, ::toggleVisibility, ::setFavourite
        )
}