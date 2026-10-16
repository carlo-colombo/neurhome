package ovh.litapp.neurhome3

import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.UserHandle
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * Replaces Robolectric's own [LauncherApps] shadow for the tests that execute the real
 * [ApplicationService.toApplication] body. The built-in shadow has no implementation for
 * [LauncherApps.getProfiles] or [LauncherApps.resolveActivity], which are exactly the two reads the
 * production method performs, and both need the launcher binder off-device.
 *
 * A test registers the profiles the platform should report in [profiles] and the launcher activity
 * each package resolves to in [activityFor], then asserts on [resolvedIntents], which records every
 * resolve call with the component and user handle it was asked to resolve.
 */
@Implements(LauncherApps::class)
class ShadowResolvableLauncherApps {
    @Implementation
    fun getProfiles(): List<UserHandle> = profiles.toList()

    @Implementation
    fun resolveActivity(intent: Intent, user: UserHandle): LauncherActivityInfo? {
        val component = checkNotNull(intent.component) {
            "resolved a launcher intent without a component: $intent"
        }
        resolvedIntents += component.packageName to user
        return activityFor[component.packageName]
    }

    companion object {
        val profiles: MutableList<UserHandle> = mutableListOf()

        /** Launcher activity each package resolves to; a package that is absent resolves to null. */
        val activityFor: MutableMap<String, LauncherActivityInfo> = mutableMapOf()

        /** Every resolve call, as the package name and the user handle it was asked about. */
        val resolvedIntents: MutableList<Pair<String, UserHandle>> = mutableListOf()

        fun reset() {
            profiles.clear()
            activityFor.clear()
            resolvedIntents.clear()
        }
    }
}
