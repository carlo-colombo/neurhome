package ovh.litapp.neurhome3

import android.app.Application
import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.os.UserManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ovh.litapp.neurhome3.data.dao.ApplicationLogEntryDao.PackageCount

/**
 * Pins the `PackageManager.NameNotFoundException` branch of the real
 * [ApplicationService.toApplication]: a package the platform refuses to name a launch activity for
 * resolves to `null` instead of failing the whole refresh.
 *
 * [ShadowNameNotFoundLaunchIntent] replaces Robolectric's `ApplicationPackageManager` shadow, so this
 * case needs its own test class instead of sharing `ApplicationServiceLookupTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = Application::class,
    shadows = [ShadowResolvableLauncherApps::class, ShadowNameNotFoundLaunchIntent::class],
)
class ApplicationServiceLaunchIntentFailureTest {
    private lateinit var service: ApplicationService

    @Before
    fun setUp() {
        ShadowResolvableLauncherApps.reset()
        val context = RuntimeEnvironment.getApplication()
        ShadowResolvableLauncherApps.profiles += userHandle(0)
        service = ApplicationService(
            packageManager = context.packageManager,
            launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps,
            userManager = context.getSystemService(Context.USER_SERVICE) as UserManager,
        )
    }

    @Test
    fun aNameNotFoundExceptionFromTheLaunchIntentLookupResolvesToNull() {
        assertNull(service.toApplication(PackageCount("com.example.gone", user = 0, score = 3.0)))
        assertEquals(emptyList<Pair<String, UserHandle>>(), ShadowResolvableLauncherApps.resolvedIntents)
    }

    private fun userHandle(userId: Int): UserHandle =
        UserHandle::class.java
            .getDeclaredConstructor(Int::class.javaPrimitiveType!!)
            .apply { isAccessible = true }
            .newInstance(userId)
}
