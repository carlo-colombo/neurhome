package ovh.litapp.neurhome3.data.repositories

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.UserManager
import androidx.room.Room
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ovh.litapp.neurhome3.ApplicationService
import ovh.litapp.neurhome3.ShadowApplicationService
import ovh.litapp.neurhome3.data.AppDatabase
import ovh.litapp.neurhome3.data.Application as HomeApplication
import ovh.litapp.neurhome3.data.dao.ApplicationLogEntryDao
import ovh.litapp.neurhome3.data.models.ApplicationLogEntry
import ovh.litapp.neurhome3.data.ml.PairwiseHomeAppReranker

/**
 * Covers the intent-only row branch of the Home ranking. A starred contact is launched through an
 * `ACTION_CALL` intent, so the key its launch is logged under is its call URI, and the ranking has
 * to resolve that key back to the loaded contact row: the package lookup this test's
 * [ShadowApplicationService] stands in for can only turn a real package name into a row, so a
 * `tel:` candidate that reached it would be silently dropped from the ranking.
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

    private fun ranking(source: HomeAppRankingSource) = HomeAppRanking(
        source = source,
        resolver = ContactRowHomeAppResolver(
            knownContacts = { listOf(contact) },
            delegate = ApplicationServiceHomeAppResolver(applicationService()),
        ),
        reranker = PairwiseHomeAppReranker(),
    )

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

        fun callUri(number: String): Uri = Uri.fromParts("tel", number, null)
    }
}
