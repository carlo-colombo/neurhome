package ovh.litapp.neurhome3.data

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.net.Uri
import android.os.UserHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins [stableListKey] as the row identity of the application lists. Package and profile alone are
 * not unique when one package exposes several launcher activities, so the key has to keep the
 * launcher component in it; a collision makes Compose reuse a row for the wrong application and
 * drops a duplicate key into the `LazyColumn`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ApplicationStableListKeyTest {
    @Test
    fun theSameLauncherActivityAlwaysKeepsTheSameKey() {
        val info = launcherActivityInfo("com.example.app", ".MainActivity", userId = 0)

        assertEquals(
            "com.example.app:${info.user.hashCode()}:${info.componentName.flattenToString()}",
            application(info = info).stableListKey(),
        )
    }

    @Test
    fun twoLauncherActivitiesOfOnePackageGetDifferentKeys() {
        val main = application(launcherActivityInfo("com.example.app", ".MainActivity", userId = 0))
        val second = application(launcherActivityInfo("com.example.app", ".SettingsActivity", userId = 0))

        assertEquals(main.packageName, second.packageName)
        assertNotEquals(main.stableListKey(), second.stableListKey())
    }

    @Test
    fun theSameLauncherActivityInTwoProfilesGetsDifferentKeys() {
        val personal = application(launcherActivityInfo("com.example.app", ".MainActivity", userId = 0))
        val work = application(launcherActivityInfo("com.example.app", ".MainActivity", userId = 10))

        assertTrue(personal.stableListKey().startsWith("com.example.app:${userHandle(0).hashCode()}:"))
        assertTrue(work.stableListKey().startsWith("com.example.app:${userHandle(10).hashCode()}:"))
        assertNotEquals(personal.stableListKey(), work.stableListKey())
    }

    @Test
    fun withoutALauncherActivityTheKeyFallsBackToTheIntentDataThenTheLabel() {
        val byIntent = Application(
            label = "Label",
            packageName = "com.example.app",
            icon = null,
            intent = Intent(Intent.ACTION_VIEW, Uri.parse("content://com.example/row")),
        )
        val byLabel = Application(label = "Label", packageName = "com.example.app", icon = null)

        assertEquals("com.example.app:0:content://com.example/row", byIntent.stableListKey())
        assertEquals("com.example.app:0:Label", byLabel.stableListKey())
        // Two rows that share the label would collide, which is why the production lists always
        // carry a launcher activity or an intent.
        assertNotEquals(
            byLabel.stableListKey(),
            byLabel.copy(label = "Other label").stableListKey(),
        )
    }

    @Test
    fun intentOnlyRowsKeepOneKeyPerCallUri() {
        val ada = contact("Ada", "+39111")
        val bob = contact("Bob", "+39222")

        // A contact row is identified by its call URI rather than by a launcher component, and the
        // two contacts keep distinct keys, which is what keeps remembered row state from leaking
        // from one contact to another.
        assertEquals("${ada.packageName}:0:${ada.packageName}", ada.stableListKey())
        assertNotEquals(ada.stableListKey(), bob.stableListKey())
    }

    private fun contact(label: String, number: String): Application {
        val callUri = Uri.fromParts("tel", number, null)
        return Application(
            label = label,
            packageName = callUri.toString(),
            icon = null,
            intent = Intent(Intent.ACTION_CALL, callUri),
        )
    }

    private fun application(info: LauncherActivityInfo) = Application(
        label = "App",
        packageName = "com.example.app",
        icon = null,
        appInfo = info,
    )

    /**
     * Builds a real [LauncherActivityInfo] for one launcher activity of one profile.
     * `LauncherActivityInfo` is created by `LauncherApps.resolveActivity`, which needs the launcher
     * binder and returns null off-device, and its constructors are hidden API. Robolectric's
     * android-all runtime still carries them, so the test reaches them reflectively instead of
     * weakening the assertion.
     */
    private fun launcherActivityInfo(
        packageName: String,
        activityName: String,
        userId: Int,
    ): LauncherActivityInfo {
        val activityInfo = ActivityInfo().apply {
            this.packageName = packageName
            this.name = activityName
            this.applicationInfo = ApplicationInfo().apply {
                this.packageName = packageName
                this.name = packageName
            }
        }
        val internalClass = Class.forName("android.content.pm.LauncherActivityInfoInternal")
        val internal = internalClass.getDeclaredConstructor(
            ActivityInfo::class.java,
            Class.forName("android.content.pm.IncrementalStatesInfo"),
            UserHandle::class.java,
        ).apply { isAccessible = true }
            .newInstance(activityInfo, null, userHandle(userId))
        return LauncherActivityInfo::class.java
            .getDeclaredConstructor(Context::class.java, internalClass)
            .apply { isAccessible = true }
            .newInstance(RuntimeEnvironment.getApplication(), internal)
    }

    private fun userHandle(userId: Int): UserHandle =
        UserHandle::class.java
            .getDeclaredConstructor(Int::class.javaPrimitiveType!!)
            .apply { isAccessible = true }
            .newInstance(userId)
}
