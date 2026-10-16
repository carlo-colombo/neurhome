package ovh.litapp.neurhome3.ui

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.LauncherActivityInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.UserHandle
import android.os.UserManager
import android.provider.ContactsContract
import androidx.room.Room
import androidx.room.withTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ContentProviderController
import ovh.litapp.neurhome3.ApplicationService
import ovh.litapp.neurhome3.application.NeurhomeApplication
import ovh.litapp.neurhome3.data.AppDatabase
import ovh.litapp.neurhome3.data.Application as HomeApplication
import ovh.litapp.neurhome3.data.ApplicationVisibility
import ovh.litapp.neurhome3.data.dao.ContactsDAO
import ovh.litapp.neurhome3.data.models.ApplicationLogEntry
import ovh.litapp.neurhome3.data.models.WifiContext
import ovh.litapp.neurhome3.data.repositories.FavouritesRepository
import ovh.litapp.neurhome3.data.repositories.NeurhomeRepository
import ovh.litapp.neurhome3.data.repositories.RoomHomeAppRankingSource
import ovh.litapp.neurhome3.data.repositories.TagRepository

/**
 * Drives the real launch path for both row kinds through [NeurhomeViewModel] and the real
 * repository on the application's database.
 *
 * The identity contract it pins is the one the ranking depends on: a launch is logged under the
 * row's own identity, so a starred contact's launch has to be logged under the same call URI the
 * row is identified by — logging anything else (`tel:` data resolved differently, or the dialer
 * package) leaves the contact permanently unrankable. The launcher-row side of the same wiring is
 * pinned too, because the uninstall guard and the track flag both branch on how the row is
 * launched.
 */
@RunWith(RobolectricTestRunner::class)
class ContactLaunchTrackingTest {
    private lateinit var application: NeurhomeApplication
    private lateinit var database: AppDatabase
    private lateinit var viewModel: TestNeurhomeViewModel
    private val startedIntents = mutableListOf<Intent>()

    @Before
    fun setUp() {
        application = RuntimeEnvironment.getApplication() as NeurhomeApplication
        // `launch` logs a contact under the profile the launcher reports first, which a device
        // always has; the shadow has to be given one.
        val userManager = application.getSystemService(Context.USER_SERVICE) as UserManager
        shadowOf(userManager).addProfile(0, 0, "User", 0)

        // The launch log lives in an in-memory database so the assertions below read exactly what
        // this test's launch wrote, whatever database the application graph holds.
        database = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val applicationService = ApplicationService(
            packageManager = application.packageManager,
            launcherApps = application.launcherApps,
            userManager = userManager,
        )
        val repository = NeurhomeRepository(
            applicationLogEntryDao = database.applicationLogEntryDao(),
            additionalPackageMetadataDao = database.hiddenPackageDao(),
            applicationService = applicationService,
            contactsDAO = ContactsDAO(application),
            application = application,
            database = database,
            launcherApps = application.launcherApps,
            homeAppRankingSource = RoomHomeAppRankingSource(
                applicationLogEntryDao = database.applicationLogEntryDao(),
                quietModes = { emptyMap() },
            ),
            tagRepository = TagRepository(database.tagDao(), database.applicationTagDao()) { block ->
                database.withTransaction { block() }
            },
            databaseUriProvider = { Uri.EMPTY },
        )
        viewModel = TestNeurhomeViewModel(
            NeurhomeViewModelDependencies(
                neurhomeRepository = repository,
                favouritesRepository = FavouritesRepository(database.settingDao(), applicationService),
                startActivity = { startedIntents += it },
                getWifiContext = { WifiContext.UNKNOWN },
                getPositionForLogging = { null },
                launcherApps = application.launcherApps,
                checkPermission = { true },
            )
        )
    }

    @Test
    fun launchingAContactStartsTheCallAndTracksItUnderTheRowIdentity() {
        val contact = starredContact("Ada", "+3912345678")

        viewModel.launch(contact, track = true)

        val launched = startedIntents.single()
        assertEquals(Intent.ACTION_CALL, launched.action)
        assertEquals("tel", launched.data?.scheme)
        assertEquals("+3912345678", launched.data?.schemeSpecificPart)
        assertTrue(
            "the call must be started as a new task",
            launched.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0,
        )
        val logged = awaitLoggedLaunch()
        assertEquals(
            "the logged key must be the row identity, or the ranking cannot resolve it",
            contact.packageName,
            logged.packageName,
        )
        assertEquals(
            contact.intent?.data.toString(),
            logged.packageName,
        )
    }

    @Test
    fun anIntentOnlyRowCannotBeUninstalled() {
        val contact = starredContact("Ada", "+3912345678")

        viewModel.appActions.management.remove(contact)

        assertEquals(
            "package:tel:+3912345678 is not an uninstall request the platform can honour",
            emptyList<Intent>(),
            startedIntents,
        )
    }

    @Test
    fun aLauncherRowIsStillUninstalledByItsPackage() {
        val row = launcherRow("com.example.app")

        viewModel.appActions.management.remove(row)

        val uninstall = startedIntents.single()
        assertEquals(Intent.ACTION_DELETE, uninstall.action)
        assertEquals(Uri.parse("package:com.example.app"), uninstall.data)
    }

    @Test
    fun trackingFollowsHowARowIsLaunchedAndNotItsVisibility() {
        assertTrue(launcherRow("com.example.app").canTrack)
        assertTrue(
            "hiding a row from the top list does not stop a launch that happens",
            launcherRow("com.example.app", ApplicationVisibility.HIDDEN_FROM_TOP).canTrack,
        )
        assertTrue(starredContact("Ada", "+3912345678").canTrack)
        assertFalse(
            "a row that cannot be launched must not claim tracking",
            HomeApplication(label = "Placeholder", packageName = "com.example.placeholder", icon = null).canTrack,
        )
    }

    private fun starredContact(name: String, number: String): HomeApplication {
        val provider = ContentProviderController
            .of(FakeContactsProvider())
            .create(ContactsContract.AUTHORITY)
            .get()
        provider.rows = listOf(name to number)
        return ContactsDAO(application).getStarredContacts().single()
    }

    private fun launcherRow(
        packageName: String,
        visibility: ApplicationVisibility = ApplicationVisibility.VISIBLE,
    ) = HomeApplication(
        label = "App",
        packageName = packageName,
        icon = null,
        appInfo = launcherActivityInfo(packageName, ".MainActivity", userId = 0),
        visibility = visibility,
    )

    /**
     * `launch` logs the launch on the repository's IO scope, so the write is awaited rather than
     * assumed to have landed when the call returns.
     */
    private fun awaitLoggedLaunch(): ApplicationLogEntry {
        val dao = database.applicationLogEntryDao()
        val deadline = System.nanoTime() + AWAIT_TIMEOUT_NANOS
        while (true) {
            val entries = dao.all()
            if (entries.isNotEmpty()) return entries.single()
            check(System.nanoTime() < deadline) { "the launch was never logged" }
            Thread.sleep(AWAIT_POLL_MILLIS)
        }
    }

    /**
     * Builds a real [LauncherActivityInfo]. Its constructors are hidden API and the type is only
     * produced by `LauncherApps.resolveActivity`, which needs the launcher binder off-device, so the
     * test reaches Robolectric's constructor reflectively instead of weakening the row it builds.
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

    /** A view model over the real dependencies; nothing in this test needs a concrete subclass. */
    private class TestNeurhomeViewModel(dependencies: NeurhomeViewModelDependencies) :
        NeurhomeViewModel(dependencies)

    /** Serves the starred contact rows the production projection reads, by index. */
    private class FakeContactsProvider : ContentProvider() {
        var rows: List<Pair<String, String>> = emptyList()

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            val columns = projection ?: return MatrixCursor(emptyArray())
            return MatrixCursor(columns).apply {
                rows.forEachIndexed { index, (name, number) ->
                    addRow(
                        arrayOf<Any?>(
                            index.toLong(),
                            name,
                            "lookup-$index",
                            null,
                            number,
                            "1",
                        )
                    )
                }
            }
        }

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0
    }

    private companion object {
        const val AWAIT_TIMEOUT_NANOS = 5_000_000_000L
        const val AWAIT_POLL_MILLIS = 10L
    }
}
