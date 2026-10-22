package ovh.litapp.neurhome3.data.repositories

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import android.os.UserManager
import android.provider.ContactsContract
import androidx.room.Room
import androidx.room.withTransaction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ContentProviderController
import org.robolectric.annotation.Config
import ovh.litapp.neurhome3.ApplicationService
import ovh.litapp.neurhome3.ShadowApplicationService
import ovh.litapp.neurhome3.application.NeurhomeApplication
import ovh.litapp.neurhome3.data.AppDatabase
import ovh.litapp.neurhome3.data.Application as HomeApplication
import ovh.litapp.neurhome3.data.dao.ApplicationLogEntryDao
import ovh.litapp.neurhome3.data.dao.ContactsDAO
import ovh.litapp.neurhome3.data.models.ApplicationLogEntry
import ovh.litapp.neurhome3.data.models.Setting
import ovh.litapp.neurhome3.data.ml.PairwiseHomeAppReranker

/**
 * Covers the intent-only row branch of the Home ranking and of the statistics simulation. A starred
 * contact is launched through an `ACTION_CALL` intent, so the key its launch is logged under is its
 * call URI, and both reads have to resolve that key back to the loaded contact row: the package
 * lookup this test's [ShadowApplicationService] stands in for can only turn a real package name into
 * a row, so a `tel:` candidate that reached it would be silently dropped.
 *
 * The ranking maths and the resolved-row cache are covered by `HomeAppRankingTest` and
 * `CachedHomeAppResolverTest`; this pins the identity path through them with the real SQL source.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, shadows = [ShadowApplicationService::class])
class ContactRowRankingTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: ApplicationLogEntryDao

    private val now: LocalDateTime = LocalDateTime.now().withNano(0)

    /** The key the contact's launches are logged under, which is also its row identity. */
    private val callKey = callUri(CONTACT_NUMBER).toString()

    private val contact = HomeApplication(
        label = "Ada",
        packageName = callKey,
        icon = null,
        intent = Intent(Intent.ACTION_CALL, callUri(CONTACT_NUMBER)),
    )

    @Before
    fun setUp() {
        ShadowApplicationService.reset()
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.applicationLogEntryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aLoggedCallUriResolvesToItsContactRowInsteadOfThePackageLookup() {
        val lookups = mutableListOf<HomeAppCandidate>()
        val resolver = ContactRowHomeAppResolver(
            knownContacts = { listOf(contact) },
            delegate = { candidate -> lookups += candidate; null },
        )

        val resolved = resolver.resolve(HomeAppCandidate(callKey, user = 0, score = 4.5))

        assertEquals(contact.copy(score = 4.5), resolved)
        assertEquals("a tel: key must not reach the package lookup", emptyList<HomeAppCandidate>(), lookups)
    }

    @Test
    fun aKeyNoLoadedContactCarriesFallsThroughToThePackageLookup() {
        val lookups = mutableListOf<HomeAppCandidate>()
        val resolver = ContactRowHomeAppResolver(
            knownContacts = { listOf(contact) },
            delegate = { candidate -> lookups += candidate; null },
        )

        val resolved = resolver.resolve(HomeAppCandidate("com.example.gone", user = 0, score = 1.0))

        assertNull(resolved)
        assertEquals(
            listOf(HomeAppCandidate("com.example.gone", user = 0, score = 1.0)),
            lookups,
        )
    }

    @Test
    fun aContactLaunchedRepeatedlyRanksAlongsideAnAppLaunchedRepeatedly() {
        repeat(5) { dao.insert(entry("com.example.target")) }
        repeat(3) { dao.insert(entry(callKey)) }
        dao.insert(entry("com.example.other"))
        ShadowApplicationService.resolved["com.example.target"] =
            HomeApplication(label = "Target", packageName = "com.example.target", icon = null)
        val source = source(quietProfiles = emptySet())

        val top = ranking(source).classicTopApps(n = 3)

        assertEquals(
            listOf("com.example.target", callKey),
            top.map { it.packageName },
        )
        assertEquals("Ada", top[1].label)
        assertEquals(contact.intent?.data.toString(), top[1].packageName)
        // The ranked contact carries the score its launches produced, exactly like an application:
        // the comparison is against the DAO's own row, because the score expression is keyed on the
        // day the test happens to run.
        val rankedContactScore = source.topAppsByScore(limit = homeCandidateLimit(3))
            .first { it.packageName == callKey }.score
        assertTrue("the contact's launches must drive its score", rankedContactScore > 0.0)
        assertEquals(rankedContactScore, top[1].score, 0.0)
    }

    /**
     * Both row kinds go through the one quiet-profile filter. A contact is logged under the profile
     * of the caller (`launcherApps.profiles[0]`, the owner, which is never quiet), so it is not
     * dropped for having no `LauncherActivityInfo`; if its profile were quiet, the same filter that
     * hides an application hides it too.
     */
    @Test
    fun anIntentOnlyCandidateIsRankedUnlessItsProfileIsQuiet() {
        repeat(3) { dao.insert(entry(callKey, user = 0)) }

        assertEquals(
            listOf(callKey),
            ranking(source(quietProfiles = setOf(10))).classicTopApps(n = 3).map { it.packageName },
        )
        assertEquals(
            emptyList<String>(),
            ranking(source(quietProfiles = setOf(0))).classicTopApps(n = 3).map { it.packageName },
        )
    }

    /**
     * The statistics simulation reads its rows out of the same launch log, so it has to resolve the
     * same keys: through the package lookup alone every launch of a contact was dropped and the
     * contact could never be listed, however often it had been called. This drives the production
     * read end to end - the real SQL, the repository's contact rows and the identity-first
     * resolution - because that is where the two reads have to agree.
     */
    @Test
    @Config(application = NeurhomeApplication::class, shadows = [ShadowApplicationService::class])
    fun aContactLaunchedOftenAppearsInTheStatisticsSimulation() {
        val application = RuntimeEnvironment.getApplication() as NeurhomeApplication
        shadowOf(application).grantPermissions(Manifest.permission.READ_CONTACTS)
        // Written through the DAO, because setContactCandidates writes on its own IO scope and the
        // application's database refuses main-thread queries.
        runBlocking(Dispatchers.IO) {
            application.settingsRepository.settingDao
                .upsert(Setting(CONTACT_CANDIDATES_SETTING, ContactCandidates.STARRED.name))
        }
        ContentProviderController.of(FakeContactsProvider(listOf("Ada" to CONTACT_NUMBER)))
            .create(ContactsContract.AUTHORITY)
        ShadowApplicationService.resolved["com.example.target"] =
            HomeApplication(label = "Target", packageName = "com.example.target", icon = null)
        repeat(5) { dao.insert(entry(callKey)) }
        repeat(3) { dao.insert(entry("com.example.target")) }
        val repository = repository(application)

        val apps = awaitSimulatedTopApps(
            repository,
            dayOfWeek = now.dayOfWeek.value % 7,
            minuteOfDay = now.hour * 60 + now.minute,
            until = { rows -> rows.any { it.packageName == callKey } },
        )

        assertEquals(
            "the contact is ranked by its own launches, ahead of the app it outranks",
            listOf(callKey, "com.example.target"),
            apps.map { it.packageName },
        )
        assertEquals("Ada", apps.first().label)
        assertEquals(Intent.ACTION_CALL, apps.first().intent?.action)
    }

    private fun ranking(source: HomeAppRankingSource) = HomeAppRanking(
        source = source,
        resolver = ContactRowHomeAppResolver(
            knownContacts = { listOf(contact) },
            delegate = ApplicationServiceHomeAppResolver(applicationService()),
        ),
        reranker = PairwiseHomeAppReranker(),
    )

    /**
     * The statistics read is the one flow here whose sharing coroutine runs on the repository's Main
     * scope, so its emissions land as dispatches on the Robolectric main looper. The flow is
     * collected from an IO thread and the looper is drained until [until] holds, because the contact
     * rows load asynchronously: the read's first emission still resolves against the contacts
     * flow's initial empty list, so waiting for the rows is what makes the assertion meaningful.
     */
    private fun awaitSimulatedTopApps(
        repository: NeurhomeRepository,
        dayOfWeek: Int,
        minuteOfDay: Int,
        until: (List<HomeApplication>) -> Boolean,
    ): List<HomeApplication> {
        val result = CompletableFuture<List<HomeApplication>>()
        val collection = CoroutineScope(Dispatchers.IO).launch {
            result.complete(
                repository.getSimulatedTopApps(dayOfWeek, minuteOfDay, wifi = null).first(until)
            )
        }
        val deadline = System.nanoTime() + AWAIT_TIMEOUT_NANOS
        while (!result.isDone) {
            shadowOf(Looper.getMainLooper()).idle()
            check(System.nanoTime() < deadline) { "the simulated ranking never emitted the expected rows" }
            Thread.sleep(AWAIT_POLL_MILLIS)
        }
        collection.cancel()
        repository.close()
        return result.getNow(emptyList())
    }

    /**
     * A [NeurhomeRepository] over this test's in-memory launch log and the application's own
     * settings, so the contact rows it resolves against are the ones the faked contacts provider
     * serves to the production [ContactsDAO] read.
     */
    private fun repository(application: NeurhomeApplication) = NeurhomeRepository(
        applicationLogEntryDao = dao,
        additionalPackageMetadataDao = database.hiddenPackageDao(),
        applicationService = ApplicationService(
            packageManager = application.packageManager,
            launcherApps = application.launcherApps,
            userManager = application.getSystemService(Context.USER_SERVICE) as UserManager,
        ),
        contactsDAO = ContactsDAO(application),
        application = application,
        database = database,
        launcherApps = application.launcherApps,
        homeAppRankingSource = RoomHomeAppRankingSource(
            applicationLogEntryDao = dao,
            quietModes = { emptyMap() },
        ),
        tagRepository = TagRepository(database.tagDao(), database.applicationTagDao()) { block ->
            database.withTransaction { block() }
        },
        databaseUriProvider = { Uri.EMPTY },
    )

    /** Serves the starred-contact rows the production projection reads, by index. */
    private class FakeContactsProvider(
        private val rows: List<Pair<String, String>>,
    ) : ContentProvider() {
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
                            number,
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

    private fun source(quietProfiles: Set<Int>) = RoomHomeAppRankingSource(
        applicationLogEntryDao = dao,
        quietModes = { quietProfiles.associateWith { true } },
    )

    private fun applicationService(): ApplicationService {
        val context = RuntimeEnvironment.getApplication()
        return ApplicationService(
            packageManager = context.packageManager,
            launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps,
            userManager = context.getSystemService(Context.USER_SERVICE) as UserManager,
        )
    }

    private fun entry(packageName: String, user: Int = 0) = ApplicationLogEntry(
        packageName = packageName,
        timestamp = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
        wifi = null,
        latitude = null,
        longitude = null,
        geohash = null,
        user = user,
    )

    private companion object {
        const val CONTACT_NUMBER = "+3912345678"

        const val AWAIT_TIMEOUT_NANOS = 5_000_000_000L

        const val AWAIT_POLL_MILLIS = 5L

        fun callUri(number: String): Uri = Uri.fromParts("tel", number, null)
    }
}
