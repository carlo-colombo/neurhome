package ovh.litapp.neurhome3

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.ColorDrawable
import android.os.UserHandle
import android.os.UserManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import ovh.litapp.neurhome3.data.dao.ApplicationLogEntryDao.PackageCount

/**
 * Runs the real [ApplicationService.toApplication] body against the platform services: the launcher
 * reads are answered by [ShadowResolvableLauncherApps], the quiet-mode reads by
 * [ShadowQuietModeUserManager], and the packaging reads by Robolectric's package-manager shadow. Every
 * early return and every field of the produced [Application] is therefore exercised here instead of
 * being replaced by a shadow of `toApplication` itself.
 *
 * The branches pinned below are the ones that silently drop or mis-attribute a row in Home and the
 * favourites slots: a package without a launch intent, a launch intent the launcher cannot resolve,
 * a request for a profile the device does not have, and the label/icon/score the row is built from.
 * The `NameNotFoundException` branch lives in `ApplicationServiceLaunchIntentFailureTest`, which
 * needs a stricter package-manager shadow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = Application::class,
    shadows = [ShadowResolvableLauncherApps::class, ShadowQuietModeUserManager::class],
)
class ApplicationServiceLookupTest {
    private lateinit var context: Context
    private lateinit var packageManager: PackageManager
    private lateinit var userManager: UserManager
    private lateinit var service: ApplicationService

    @Before
    fun setUp() {
        ShadowResolvableLauncherApps.reset()
        ShadowQuietModeUserManager.reset()
        context = RuntimeEnvironment.getApplication()
        packageManager = context.packageManager
        userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        service = ApplicationService(
            packageManager = packageManager,
            launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps,
            userManager = userManager,
        )
    }

    @Test
    fun aResolvablePackageBecomesARowWithTheLauncherLabelBadgedIconAndScore() {
        val icon = ColorDrawable(0xff00ff00.toInt())
        val personal = userHandle(0)
        val launcher = launcherActivityInfo(
            packageName = MAIL,
            activityName = "$MAIL.MainActivity",
            user = personal,
            label = "Mail",
        )
        launcherProfiles(personal)
        ShadowResolvableLauncherApps.activityFor[MAIL] = launcher
        installLauncherActivity(MAIL, "$MAIL.MainActivity")
        // The icon the launcher activity reports for its package, which `getBadgedIcon(0)` reads.
        shadowOf(packageManager).setUnbadgedApplicationIcon(MAIL, icon)

        val application = service.toApplication(PackageCount(MAIL, user = 0, score = 2.5))

        assertEquals("Mail", application?.label)
        assertEquals(MAIL, application?.packageName)
        assertSame("the row carries the resolved launcher activity's icon", icon, application?.icon)
        assertEquals(2.5, application?.score ?: 0.0, 0.0)
        assertSame(launcher, application?.appInfo)
        assertNull(application?.intent)
    }

    @Test
    fun aPackageWithoutALaunchIntentResolvesToNull() {
        launcherProfiles(userHandle(0))

        // Nothing is installed for the package, so the platform cannot name a launch activity.
        assertNull(service.toApplication(PackageCount(GONE, user = 0, score = 3.0)))
        assertEquals(emptyList<Pair<String, UserHandle>>(), ShadowResolvableLauncherApps.resolvedIntents)
    }

    @Test
    fun aPackageWhoseLaunchIntentResolvesToNoActivityResolvesToNull() {
        val personal = userHandle(0)
        launcherProfiles(personal)
        installLauncherActivity(MAIL, "$MAIL.MainActivity")

        // The launch intent exists, but the launcher has no activity for it in this profile.
        assertNull(service.toApplication(PackageCount(MAIL, user = 0, score = 3.0)))
        assertEquals(listOf(MAIL to personal), ShadowResolvableLauncherApps.resolvedIntents)
    }

    @Test
    fun aRequestForAProfileTheDeviceDoesNotHaveFallsBackToThePersonalProfile() {
        val personal = userHandle(0)
        launcherProfiles(personal, userHandle(10))
        ShadowResolvableLauncherApps.activityFor[MAIL] = launcherActivityInfo(
            packageName = MAIL,
            activityName = "$MAIL.MainActivity",
            user = personal,
            label = "Mail",
        )
        installLauncherActivity(MAIL, "$MAIL.MainActivity")

        val application = service.toApplication(PackageCount(MAIL, user = 11, score = 1.0))

        assertEquals(MAIL, application?.packageName)
        assertEquals(
            "profile 11 is absent, so the personal profile answers the lookup",
            listOf(MAIL to personal),
            ShadowResolvableLauncherApps.resolvedIntents,
        )
    }

    @Test
    fun aLookupWithNoLauncherProfileAtAllResolvesToNull() {
        // No personal profile to fall back to: there is no user handle to resolve anything for.
        assertNull(service.toApplication(PackageCount(MAIL, user = 0, score = 3.0)))
        assertEquals(emptyList<Pair<String, UserHandle>>(), ShadowResolvableLauncherApps.resolvedIntents)
    }

    @Test
    fun thePackageNameOverloadLooksUpThePersonalProfileWithoutAScore() {
        val personal = userHandle(0)
        launcherProfiles(personal)
        ShadowResolvableLauncherApps.activityFor[MAIL] = launcherActivityInfo(
            packageName = MAIL,
            activityName = "$MAIL.MainActivity",
            user = personal,
            label = "Mail",
        )
        installLauncherActivity(MAIL, "$MAIL.MainActivity")

        val application = service.toApplication(MAIL)

        assertEquals(MAIL, application?.packageName)
        assertEquals(0.0, application?.score ?: -1.0, 0.0)
        assertEquals(listOf(MAIL to personal), ShadowResolvableLauncherApps.resolvedIntents)
    }

    @Test
    fun profilesAndQuietModesAreReadOnceAndServedUntilTheyAreInvalidated() {
        val personal = userHandle(0)
        val work = userHandle(10)
        ShadowQuietModeUserManager.quietModes[personal] = false
        ShadowQuietModeUserManager.quietModes[work] = true
        launcherProfiles(personal, work)
        ShadowResolvableLauncherApps.activityFor[MAIL] = launcherActivityInfo(
            packageName = MAIL,
            activityName = "$MAIL.MainActivity",
            user = work,
            label = "Mail",
        )
        installLauncherActivity(MAIL, "$MAIL.MainActivity")

        // The lookup itself reads the profile set, which reads every profile's quiet mode once.
        service.toApplication(PackageCount(MAIL, user = 10, score = 1.0))
        assertEquals(mapOf(0 to false, 10 to true), service.quietModes())
        assertEquals(listOf(personal, work), ShadowQuietModeUserManager.reads)

        // Quiet mode is turned off and an eleventh profile appears, but the cached state still
        // answers: both are only picked up after the invalidation the launcher callback triggers.
        ShadowQuietModeUserManager.quietModes[work] = false
        launcherProfiles(userHandle(11))
        assertEquals(mapOf(0 to false, 10 to true), service.quietModes())
        assertEquals(
            "the platform is not asked again while the cache is warm",
            2,
            ShadowQuietModeUserManager.reads.size,
        )
        assertEquals(personal, service.getProfile(11))

        service.invalidateProfileState()

        assertEquals(mapOf(0 to false, 10 to false, 11 to false), service.quietModes())
        assertEquals(5, ShadowQuietModeUserManager.reads.size)
        assertEquals(userHandle(11), service.getProfile(11))
    }

    private fun launcherProfiles(vararg users: UserHandle) {
        ShadowResolvableLauncherApps.profiles += users
    }

    /** Installs [packageName] with one launcher activity, so its launch intent is resolvable. */
    private fun installLauncherActivity(packageName: String, activityName: String) {
        val installedApplication = ApplicationInfo().apply {
            this.packageName = packageName
            name = packageName
        }
        val installedActivity = ActivityInfo().apply {
            this.packageName = packageName
            name = activityName
            applicationInfo = installedApplication
            exported = true
            enabled = true
        }
        shadowOf(packageManager).installPackage(
            PackageInfo().apply {
                this.packageName = packageName
                applicationInfo = installedApplication
                activities = arrayOf(installedActivity)
            }
        )
        shadowOf(packageManager).addResolveInfoForIntent(
            launchIntent(packageName),
            ResolveInfo().apply { activityInfo = installedActivity },
        )
    }

    private fun launchIntent(packageName: String) = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setPackage(packageName)

    /**
     * Builds the [LauncherActivityInfo] the platform would hand out for one launcher activity of
     * one profile. `LauncherApps.resolveActivity` is the only production source of these objects and
     * it needs the launcher binder, so the test constructs one directly through the hidden
     * constructors the android-all runtime still carries.
     */
    private fun launcherActivityInfo(
        packageName: String,
        activityName: String,
        user: UserHandle,
        label: String,
    ): LauncherActivityInfo {
        val launcherActivity = ActivityInfo().apply {
            this.packageName = packageName
            name = activityName
            nonLocalizedLabel = label
            applicationInfo = ApplicationInfo().apply {
                this.packageName = packageName
                name = packageName
            }
        }
        val internalClass = Class.forName("android.content.pm.LauncherActivityInfoInternal")
        val internal = internalClass.getDeclaredConstructor(
            ActivityInfo::class.java,
            Class.forName("android.content.pm.IncrementalStatesInfo"),
            UserHandle::class.java,
            // API 37 added this flag; a plain launcher activity does not support multi-instance.
            java.lang.Boolean.TYPE,
        ).apply { isAccessible = true }
            .newInstance(launcherActivity, null, user, false)
        return LauncherActivityInfo::class.java
            .getDeclaredConstructor(Context::class.java, internalClass)
            .apply { isAccessible = true }
            .newInstance(context, internal)
    }

    private fun userHandle(userId: Int): UserHandle =
        UserHandle::class.java
            .getDeclaredConstructor(Int::class.javaPrimitiveType!!)
            .apply { isAccessible = true }
            .newInstance(userId)

    private companion object {
        const val MAIL = "com.example.mail"
        const val GONE = "com.example.gone"
    }
}
