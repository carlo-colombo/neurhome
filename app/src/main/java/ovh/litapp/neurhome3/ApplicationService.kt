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
    private val profiles = launcherApps.profiles.associateBy { it.hashCode() }

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

    fun quietModes(um: UserManager): Map<Int, Boolean> = profiles.keys
        .associateWith {um.isQuietModeEnabled(getProfile(it))}

    fun getProfile(user: Int): UserHandle? = profiles[user] ?: profiles[0]

    fun toApplication(packageName: String): Application? = toApplication(
        ApplicationLogEntryDao.PackageCount(
            packageName = packageName, score = 0.0, user = 0
        )
    )
}