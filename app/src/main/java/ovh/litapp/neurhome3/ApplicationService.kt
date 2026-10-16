package ovh.litapp.neurhome3

import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.UserHandle
import android.os.UserManager
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.dao.ApplicationLogEntryDao

/**
 * A platform read that must not be repeated on every refresh: [load] runs once and its result is
 * served until [invalidate]. Reading the launcher profile set and the per-profile quiet modes
 * costs a binder call each, and the home ranking reads both every 30 s.
 */
internal class BinderLookupCache<T : Any>(private val load: () -> T) {
    private var value: T? = null

    @Synchronized
    fun get(): T = value ?: load().also { value = it }

    /** Drops the cached value so the next [get] re-reads the platform. */
    @Synchronized
    fun invalidate() {
        value = null
    }
}

/** The launcher profiles and each profile's quiet mode, read together so they stay in step. */
private data class LauncherProfileState(
    val profiles: Map<Int, UserHandle>,
    val quietModes: Map<Int, Boolean>,
)

class ApplicationService(
    private val packageManager: PackageManager,
    private val launcherApps: LauncherApps,
    private val userManager: UserManager,
) {
    /**
     * Work profiles can be added or removed and their quiet mode toggled while the process lives,
     * but every candidate lookup resolves a profile and every ranking refresh reads the quiet
     * modes. Both are read together once and then served until [invalidateProfileState].
     */
    private val profileState = BinderLookupCache {
        val profiles = launcherApps.profiles.associateBy { it.hashCode() }
        LauncherProfileState(
            profiles = profiles,
            quietModes = profiles.keys.associateWith { userManager.isQuietModeEnabled(profiles[it]) },
        )
    }

    /**
     * Re-reads profiles and quiet modes on the next lookup. A work profile being added, removed,
     * or turned off/on reaches the launcher callbacks as its packages becoming (un)available for
     * that user, so the repository invalidates here from the same callback that refreshes the
     * installed applications.
     */
    fun invalidateProfileState() {
        profileState.invalidate()
    }

    /**
     * Resolves a launch-log key that names a real package. An intent-only key (a starred contact's
     * `tel:` URI) can never resolve here: it is resolved by identity from the loaded contacts
     * list before this lookup is reached.
     */
    fun toApplication(packageCount: ApplicationLogEntryDao.PackageCount): Application? {
        return try {
            val userHandle: UserHandle = getProfile(packageCount.user) ?: return null
            val intent =
                packageManager.getLaunchIntentForPackage(packageCount.packageName) ?: return null
            val launcherActivityInfo =
                launcherApps.resolveActivity(intent, userHandle) ?: return null

            Application(
                label = launcherActivityInfo.label.toString(),
                packageName = packageCount.packageName,
                icon = launcherActivityInfo.getBadgedIcon(0),
                score = packageCount.score,
                appInfo = launcherActivityInfo,
                intent = null
            )
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun quietModes(): Map<Int, Boolean> = profileState.get().quietModes

    fun getProfile(user: Int): UserHandle? {
        val profiles = profileState.get().profiles
        return profiles[user] ?: profiles[0]
    }

    fun toApplication(packageName: String): Application? = toApplication(
        ApplicationLogEntryDao.PackageCount(
            packageName = packageName, score = 0.0, user = 0
        )
    )
}