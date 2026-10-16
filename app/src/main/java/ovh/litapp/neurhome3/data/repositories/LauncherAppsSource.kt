package ovh.litapp.neurhome3.data.repositories

import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import ovh.litapp.neurhome3.ApplicationService
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.ApplicationVisibility
import ovh.litapp.neurhome3.data.dao.AdditionalPackageMetadataDao
import ovh.litapp.neurhome3.data.models.HiddenPackageType
import java.util.concurrent.CopyOnWriteArrayList

/** Launcher icon cache size, shared with the ranking's resolved-row cache. */
internal const val LAUNCHER_ICON_CACHE_SIZE = 256

/**
 * The installed launcher applications as renderable rows: the package callbacks that keep the list
 * live, the icon cache behind the rows, and the profile-state invalidation they force. Cache
 * invalidation for rows derived from these applications is announced through
 * [addPackageInvalidationListener] instead of being owned here.
 */
internal class LauncherAppsSource(
    private val applicationService: ApplicationService,
    private val launcherApps: LauncherApps,
    private val additionalPackageMetadataDao: AdditionalPackageMetadataDao,
    private val scope: CoroutineScope,
) {
    private val launcherIconCache = LauncherIconCache<Drawable>(LAUNCHER_ICON_CACHE_SIZE)
    private val packageInvalidationListeners = CopyOnWriteArrayList<(String, Int) -> Unit>()

    /**
     * Registers a callback invoked for every package change, before the refreshed list is emitted,
     * so a cache keyed on launcher rows (the ranking's resolved-row cache) drops its entry.
     */
    fun addPackageInvalidationListener(listener: (String, Int) -> Unit) {
        packageInvalidationListeners += listener
    }

    val apps: StateFlow<List<Application>> = allApps()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private fun allApps() = combine(
        listAllLauncherApps(),
        // Room re-emits the whole table on any invalidation, so identical metadata would otherwise
        // re-map and re-allocate every launcher row for nothing.
        additionalPackageMetadataDao.list().distinctUntilChanged()
    ) { launcherApps, metadata ->
        val metadataMap = metadata.associateBy { it.packageName to it.user }

        launcherApps.map { app ->
            val packageName = app.activityInfo.packageName
            val additionalPackageMetadata = metadataMap[packageName to app.user.hashCode()]

            Application(
                label = app.label.toString(),
                packageName = packageName,
                icon = launcherIconCache.getOrLoad(
                    LauncherIconKey(
                        packageName = packageName,
                        profile = app.user.hashCode(),
                        componentName = app.componentName.flattenToString(),
                    )
                ) { app.getBadgedIcon(0) },
                visibility = when (additionalPackageMetadata?.hideFrom) {
                    HiddenPackageType.TOP -> ApplicationVisibility.HIDDEN_FROM_TOP
                    HiddenPackageType.FILTERED -> ApplicationVisibility.HIDDEN_FROM_FILTERED
                    else -> ApplicationVisibility.VISIBLE
                },
                appInfo = app,
                intent = null,
                alias = additionalPackageMetadata?.alias ?: ""
            )
        }
    }.flowOn(Dispatchers.IO)

    private fun listAllLauncherApps() = callbackFlow {
        fun refresh() {
            // A work profile added, removed, or turned off/on reaches us as its packages becoming
            // (un)available for that user, so the cached profile/quiet-mode state is dropped here
            // instead of being re-read by every home refresh.
            applicationService.invalidateProfileState()
            trySend(launcherApps.profiles.flatMap { launcherApps.getActivityList(null, it) })
        }

        fun invalidate(packageName: String, user: UserHandle) {
            val profile = user.hashCode()
            launcherIconCache.invalidatePackage(packageName, profile)
            packageInvalidationListeners.forEach { it(packageName, profile) }
        }

        refresh()

        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String, user: UserHandle) {
                invalidate(packageName, user)
                refresh()
            }

            override fun onPackageRemoved(packageName: String, user: UserHandle) {
                invalidate(packageName, user)
                refresh()
            }

            override fun onPackageChanged(packageName: String, user: UserHandle) {
                invalidate(packageName, user)
                refresh()
            }

            override fun onPackagesAvailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean
            ) {
                packageNames.forEach { invalidate(it, user) }
                refresh()
            }

            override fun onPackagesUnavailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean
            ) {
                packageNames.forEach { invalidate(it, user) }
                refresh()
            }
        }

        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))

        awaitClose {
            launcherApps.unregisterCallback(callback)
        }
    }.flowOn(Dispatchers.IO)
}
