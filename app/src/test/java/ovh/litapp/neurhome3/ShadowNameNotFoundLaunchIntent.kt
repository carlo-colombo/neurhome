package ovh.litapp.neurhome3

import android.content.Intent
import android.content.pm.PackageManager
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowApplicationPackageManager

/**
 * Makes `PackageManager.getLaunchIntentForPackage` reject every package with
 * [PackageManager.NameNotFoundException], the failure [ApplicationService.toApplication] catches to
 * resolve `null`.
 *
 * Robolectric's [ShadowApplicationPackageManager] answers the lookup with a plain `null` for a
 * package it cannot resolve and never throws, so the catch clause in the production body is
 * unreachable with the default shadow. Subclassing the default shadow keeps every other package
 * read (the activity lists, the badged-icon lookup) intact and overrides only the one call whose
 * checked exception the production code handles.
 *
 * `className` is used instead of a class literal because `ApplicationPackageManager` is hidden API
 * and is not on the compile classpath of unit tests.
 */
@Implements(className = "android.app.ApplicationPackageManager")
class ShadowNameNotFoundLaunchIntent : ShadowApplicationPackageManager() {
    @Implementation
    protected override fun getLaunchIntentForPackage(packageName: String?): Intent? =
        throw PackageManager.NameNotFoundException(packageName)
}
