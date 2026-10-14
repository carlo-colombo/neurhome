package ovh.litapp.neurhome3

import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.UserHandle
import android.os.UserManager
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.dao.ApplicationLogEntryDao

class ApplicationService(
    private val packageManager: PackageManager,
    private val launcherApps: LauncherApps,
) {
    /**
     * Profiles are re-read on every lookup: work profiles can be added or removed while the
     * process lives, and a constructor snapshot would keep resolving against a stale set.
     */
    private fun currentProfiles(): Map<Int, UserHandle> =
        launcherApps.profiles.associateBy { it.hashCode() }

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

    fun quietModes(um: UserManager): Map<Int, Boolean> {
        val profiles = currentProfiles()
        return profiles.keys.associateWith { um.isQuietModeEnabled(profiles[it]) }
    }

    fun getProfile(user: Int): UserHandle? {
        val profiles = currentProfiles()
        return profiles[user] ?: profiles[0]
    }

    fun toApplication(packageName: String): Application? = toApplication(
        ApplicationLogEntryDao.PackageCount(
            packageName = packageName, score = 0.0, user = 0
        )
    )
}