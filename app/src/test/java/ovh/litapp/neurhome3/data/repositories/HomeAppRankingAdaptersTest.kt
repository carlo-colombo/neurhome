package ovh.litapp.neurhome3.data.repositories

import android.app.Application
import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ovh.litapp.neurhome3.ApplicationService
import ovh.litapp.neurhome3.ShadowApplicationService
import ovh.litapp.neurhome3.data.Application as HomeApplication
import ovh.litapp.neurhome3.data.dao.ApplicationLogEntryDao
import ovh.litapp.neurhome3.data.dao.ApplicationLogEntryDao.PackageCount
import ovh.litapp.neurhome3.data.models.ApplicationLogEntry

/**
 * Covers the two adapters between the Home ranking and the platform in
 * `HomeAppRankingAdapters.kt`: [RoomHomeAppRankingSource]'s mapping from DAO rows to
 * [HomeAppCandidate]s (with the quiet-profile set read through the provider) and
 * [ApplicationServiceHomeAppResolver]'s forwarding of a candidate into the package lookup.
 * [ApplicationService] itself is replaced by [ShadowApplicationService]; the ranking maths on top
 * of these adapters is covered by `HomeAppRankingTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, shadows = [ShadowApplicationService::class])
class HomeAppRankingAdaptersTest {
    @Before
    fun setUp() {
        ShadowApplicationService.reset()
    }

    @Test
    fun topAppsByScoreRowsBecomeCandidatesWithTheirPackageProfileAndScore() {
        val dao = FakeApplicationLogEntryDao(
            rankedApps = listOf(
                PackageCount("com.example.first", user = 0, score = 3.5),
                PackageCount("com.example.work", user = 10, score = 1.25),
            )
        )
        val source = RoomHomeAppRankingSource(dao, quietModes = { emptyMap() })

        assertEquals(
            listOf(
                HomeAppCandidate("com.example.first", user = 0, score = 3.5),
                HomeAppCandidate("com.example.work", user = 10, score = 1.25),
            ),
            source.topAppsByScore(limit = 8),
        )
        assertEquals(listOf(8), dao.requestedLimits)
    }

    @Test
    fun mostUsedAppsRowsBecomeCandidatesWithTheirPackageProfileAndScore() {
        val dao = FakeApplicationLogEntryDao(
            countedApps = listOf(PackageCount("com.example.first", user = 10, score = 4.0))
        )
        val source = RoomHomeAppRankingSource(dao, quietModes = { emptyMap() })

        assertEquals(
            listOf(HomeAppCandidate("com.example.first", user = 10, score = 4.0)),
            source.mostUsedApps(),
        )
    }

    @Test
    fun quietProfileIdsAreTheProfilesWhoseQuietModeIsCurrentlyOn() {
        val quietModes = mutableMapOf(0 to false, 10 to true)
        val source = RoomHomeAppRankingSource(
            FakeApplicationLogEntryDao(),
            quietModes = { quietModes },
        )

        assertEquals(setOf(10), source.quietProfileIds())

        // The provider is read on every call, so a profile that switches while the process lives
        // is picked up without rebuilding the source.
        quietModes[0] = true
        assertEquals(setOf(0, 10), source.quietProfileIds())

        quietModes.clear()
        assertEquals(emptySet<Int>(), source.quietProfileIds())
    }

    @Test
    fun theResolverForwardsTheCandidatesPackageProfileAndScoreToTheLookup() {
        val resolved = application()
        ShadowApplicationService.resolved["com.example.target"] = resolved
        val resolver = ApplicationServiceHomeAppResolver(applicationService())

        val application = resolver.resolve(
            HomeAppCandidate("com.example.target", user = 10, score = 2.5)
        )

        assertEquals(resolved, application)
        assertEquals(
            PackageCount("com.example.target", user = 10, score = 2.5),
            ShadowApplicationService.lookups.single(),
        )
    }

    @Test
    fun theResolverReturnsNullForACandidateTheLauncherCannotResolve() {
        val resolver = ApplicationServiceHomeAppResolver(applicationService())

        val application = resolver.resolve(HomeAppCandidate("com.example.gone", user = 0, score = 1.0))

        assertNull(application)
        assertEquals(
            listOf(PackageCount("com.example.gone", user = 0, score = 1.0)),
            ShadowApplicationService.lookups,
        )
    }

    /**
     * A real [ApplicationService] over Robolectric's platform services: its `toApplication` is
     * shadowed, and the constructor only captures the platform handles.
     */
    private fun applicationService(): ApplicationService {
        val context = RuntimeEnvironment.getApplication()
        return ApplicationService(
            packageManager = context.packageManager,
            launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps,
            userManager = context.getSystemService(Context.USER_SERVICE) as UserManager,
        )
    }

    private fun application() =
        HomeApplication(label = "Target", packageName = "com.example.target", icon = null)

    /**
     * Only the two ranking reads of [ApplicationLogEntryDao] are meaningful for these adapters;
     * every other query fails loudly so a test cannot silently depend on one of them.
     */
    private class FakeApplicationLogEntryDao(
        private val rankedApps: List<PackageCount> = emptyList(),
        private val countedApps: List<PackageCount> = emptyList(),
    ) : ApplicationLogEntryDao {
        val requestedLimits = mutableListOf<Int>()

        override fun topAppsByScore(limit: Int, period: String): List<PackageCount> {
            requestedLimits += limit
            return rankedApps.take(limit)
        }

        override fun mostUsedApps(period: String): List<PackageCount> = countedApps

        override fun all(): List<ApplicationLogEntry> = unsupported("all")

        override fun insert(entry: ApplicationLogEntry) = unsupported("insert")

        override fun deleteOlderThan(cutoff: String): Int = unsupported("deleteOlderThan")

        override fun getUniqueSSIDs(): List<String> = unsupported("getUniqueSSIDs")

        override fun simulatedTopApps(
            dayOfWeek: Int,
            minuteOfDay: Int,
            wifi: String?,
            limit: Int,
            period: String,
        ): List<PackageCount> = unsupported("simulatedTopApps")

        private fun unsupported(query: String): Nothing =
            throw UnsupportedOperationException("$query is not read by RoomHomeAppRankingSource")
    }
}
