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
import ovh.litapp.neurhome3.data.ApplicationVisibility
import ovh.litapp.neurhome3.data.repositories.FavouritesRepository
import ovh.litapp.neurhome3.data.repositories.NeurhomeRepository
import ovh.litapp.neurhome3.data.models.WifiContext

interface INeurhomeViewModel {
    val appActions: AppActions

    /**
     * Application actions grouped by the UI concern they serve. Composables
     * receive only the callback or focused holder they use, so a change to one
     * concern does not invalidate rows that belong to a different concern.
     */
    data class AppActions(
        val launch: (Application?, track: Boolean) -> Unit = { _, _ -> },
        val management: AppManagementActions = AppManagementActions(),
        val tags: AppTagActions = AppTagActions(),
    )

    /** Per-application management: uninstall, visibility and favourites. */
    data class AppManagementActions(
        val remove: (Application) -> Unit = {},
        val toggleVisibility: (Application, ApplicationVisibility) -> Unit = { _, _ -> },
        val setFavourite: (Application, Int) -> Unit = { _, _ -> },
    )

    /** Application metadata editing: alias and tag assignments. */
    data class AppTagActions(
        val setAlias: (Application, String) -> Unit = { _, _ -> },
        val setTags: (Application, Set<String>) -> Unit = { _, _ -> },
    )
}

/**
 * Dependencies shared by every [NeurhomeViewModel]. The composition root
 * (`AppViewModelFactory`) assembles one per ViewModel creation, so the common
 * wiring is defined once instead of being repeated in each factory block.
 */
class NeurhomeViewModelDependencies(
    val neurhomeRepository: NeurhomeRepository,
    val favouritesRepository: FavouritesRepository,
    val startActivity: (Intent) -> Unit,
    val getWifiContext: () -> WifiContext,
    val getPositionForLogging: () -> Location?,
    val launcherApps: LauncherApps,
    val checkPermission: (String) -> Boolean,
)

abstract class NeurhomeViewModel(
    protected val neurhomeRepository: NeurhomeRepository,
    protected val favouritesRepository: FavouritesRepository,
    protected val startActivity: (Intent) -> Unit,
    protected val getWifiContext: () -> WifiContext,
    protected val getPositionForLogging: () -> Location?,
    protected val launcherApps: LauncherApps,
    protected val checkPermission: (String) -> Boolean
) : ViewModel(), INeurhomeViewModel {
    constructor(dependencies: NeurhomeViewModelDependencies) : this(
        dependencies.neurhomeRepository,
        dependencies.favouritesRepository,
        dependencies.startActivity,
        dependencies.getWifiContext,
        dependencies.getPositionForLogging,
        dependencies.launcherApps,
        dependencies.checkPermission
    )

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
                        getWifiContext(),
                        getPositionForLogging(),
                        query
                    )
                }
            } else if (application.intent != null && checkPermission(Manifest.permission.CALL_PHONE)) {
                val intent = application.intent
                intent.flags = FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                if (track) {
                    // The row identity is the logged key: for an intent-only row that is the call
                    // URI, so the ranking can resolve the launch back to this row. A package name
                    // the platform cannot resolve here would leave the contact permanently
                    // unranked.
                    neurhomeRepository.logLaunch(
                        application.packageName,
                        launcherApps.profiles[0].hashCode(),
                        getWifiContext(),
                        getPositionForLogging(),
                        query
                    )
                }
            }
        }
    }

    private fun remove(application: Application) {
        // An intent-only row's package name is its call URI, and `package:tel:+39...` is not an
        // uninstall request the platform can honour, so only a row with a launcher activity has a
        // package to uninstall.
        if (application.appInfo == null) return
        val intent = Intent(ACTION_DELETE)
        intent.flags = FLAG_ACTIVITY_NEW_TASK
        intent.data = Uri.parse("package:${application.packageName}")
        startActivity(intent)
    }

    private fun toggleVisibility(application: Application, visibility: ApplicationVisibility) =
        neurhomeRepository.toggleVisibility(
            application,
            visibility = visibility
        )

    private fun setFavourite(application: Application, index: Int) =
        favouritesRepository.setFavourite(application, index)

    override val appActions: INeurhomeViewModel.AppActions =
        INeurhomeViewModel.AppActions(
            launch = ::launch,
            management = INeurhomeViewModel.AppManagementActions(
                remove = ::remove,
                toggleVisibility = ::toggleVisibility,
                setFavourite = ::setFavourite,
            ),
            tags = INeurhomeViewModel.AppTagActions(
                setAlias = neurhomeRepository::setAlias,
                setTags = neurhomeRepository::setTags,
            ),
        )
}
