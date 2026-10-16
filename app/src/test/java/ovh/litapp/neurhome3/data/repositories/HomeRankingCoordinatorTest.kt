package ovh.litapp.neurhome3.data.repositories

import android.app.Application
import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserManager
import java.time.Duration
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import ovh.litapp.neurhome3.ApplicationService
import ovh.litapp.neurhome3.data.dao.AdditionalPackageMetadataDao
import ovh.litapp.neurhome3.data.dao.ApplicationLogEntryDao
import ovh.litapp.neurhome3.data.dao.ApplicationLogEntryDao.PackageCount
import ovh.litapp.neurhome3.data.dao.UpdateAlias
import ovh.litapp.neurhome3.data.dao.UpdateVisibility
import ovh.litapp.neurhome3.data.models.AdditionalPackageMetadata
import ovh.litapp.neurhome3.data.models.ApplicationLogEntry

/**
 * Pins the debounce/generation semantics [HomeRankingCoordinator] owns, which nothing covered
 * before. Time is virtual ([runTest]) for the 2 s refresh debounce, while
 * [ShadowSystemClock] moves `SystemClock.elapsedRealtime`, which is what the 15 minute minimum
 * training interval is measured against; the training pass itself runs on the coordinator's own
 * `Dispatchers.IO`, so the fake DAO holds it with a gate the test opens deliberately.
 *
 * ## What the code actually guarantees
 *
 * A refresh requested while a training pass is in flight does **not** queue a second pass and does
 * **not** restart the running one: [HomeRankingCoordinator.refreshHomeModel] returns as soon as it
 * sees an active job, on the assumption that the running pass reads the newest launches. If that
 * pass already read the log before the newer launch landed, the request is simply dropped until
 * another request arrives after the interval. So the property "a refresh requested while training
 * is not lost" is not what the implementation provides, and these tests pin the real behaviour
 * (coalesced, never duplicated, never restarted) rather than the claim.
 *
 * The publication of the trained model is not observable here: `homeModel` is private and a
 * ranking read needs the whole resolver stack, so no test asserts which model the UI would see.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class HomeRankingCoordinatorTest {

    @Test
    fun selectingLearnedTrainsAtOnceInsteadOfWaitingForTheDebounce() = runTest {
        val dao = FakeApplicationLogEntryDao()
        val selection = MutableStateFlow(HomeAppSelection.CLASSIC)
        coordinator(selection, dao)
        runCurrent()
        assertEquals(0, dao.reads)

        selection.value = HomeAppSelection.LEARNED
        runCurrent()

        // No debounce time is advanced, so only the forced refresh can have started a pass.
        dao.awaitReads(1)
    }

    @Test
    fun aBurstOfRefreshesCollapsesIntoASingleTrainingPass() = runTest {
        val dao = FakeApplicationLogEntryDao()
        val coordinator = coordinator(MutableStateFlow(HomeAppSelection.LEARNED), dao)
        runCurrent()
        dao.awaitReads(1)
        dao.awaitPassPublished()
        // The burst must collapse because of the debounce, not because the minimum interval
        // happens to swallow it.
        ShadowSystemClock.advanceBy(Duration.ofMinutes(20))

        coordinator.requestHomeModelRefresh()
        advanceTimeBy(500)
        coordinator.requestHomeModelRefresh()
        advanceTimeBy(500)
        coordinator.requestHomeModelRefresh()
        advanceTimeBy(2_100)

        dao.awaitReads(2)
        advanceTimeBy(60_000)
        assertEquals("three requests 500 ms apart must train once", 2, dao.reads)
    }

    @Test
    fun aRefreshRequestedWhileAPassIsInFlightIsCoalescedIntoThatPass() = runTest {
        val dao = FakeApplicationLogEntryDao()
        dao.holdTraining()
        val coordinator = coordinator(MutableStateFlow(HomeAppSelection.LEARNED), dao)
        runCurrent()
        dao.awaitReads(1)

        // Avoid the minimum-interval gate so the only thing that can drop the request is the
        // in-flight check.
        ShadowSystemClock.advanceBy(Duration.ofMinutes(20))
        coordinator.requestHomeModelRefresh()
        advanceTimeBy(2_100)

        assertEquals("the running pass must not be restarted or duplicated", 1, dao.reads)

        dao.releaseHeldTraining()
        dao.awaitPassPublished()
        advanceTimeBy(60_000)
        assertEquals("the dropped request must not be queued behind the pass either", 1, dao.reads)
    }

    @Test
    fun aRefreshInsideTheMinimumTrainingIntervalIsSkippedUntilItHasElapsed() = runTest {
        val dao = FakeApplicationLogEntryDao()
        val coordinator = coordinator(MutableStateFlow(HomeAppSelection.LEARNED), dao)
        runCurrent()
        dao.awaitReads(1)
        dao.awaitPassPublished()

        ShadowSystemClock.advanceBy(Duration.ofMinutes(5))
        coordinator.requestHomeModelRefresh()
        advanceTimeBy(2_100)
        assertEquals("5 minutes is inside the 15 minute minimum interval", 1, dao.reads)

        ShadowSystemClock.advanceBy(Duration.ofMinutes(20))
        coordinator.requestHomeModelRefresh()
        advanceTimeBy(2_100)
        dao.awaitReads(2)
    }

    @Test
    fun aRefreshInsideTheMinimumIntervalStartsNoPassEvenAfterTheIoDispatcherSettles() = runTest {
        val dao = FakeApplicationLogEntryDao()
        val coordinator = coordinator(MutableStateFlow(HomeAppSelection.LEARNED), dao)
        runCurrent()
        dao.awaitReads(1)
        dao.awaitPassPublished()

        // Five minutes is inside the 15 minute minimum interval, so the gate must drop the
        // request. The real-time settle is what makes this an assertion about the gate rather
        // than a race with the IO dispatcher: a pass that started anyway would have reached the
        // log long before the assertion below.
        ShadowSystemClock.advanceBy(Duration.ofMinutes(5))
        coordinator.requestHomeModelRefresh()
        advanceTimeBy(2_100)
        Thread.sleep(IO_SETTLE_MILLIS)

        assertEquals("a refresh inside the minimum interval must not train", 1, dao.reads)

        ShadowSystemClock.advanceBy(Duration.ofMinutes(20))
        coordinator.requestHomeModelRefresh()
        advanceTimeBy(2_100)
        dao.awaitReads(2)
    }

    @Test
    fun classicSelectionNeverStartsATrainingPass() = runTest {
        val dao = FakeApplicationLogEntryDao()
        val coordinator = coordinator(MutableStateFlow(HomeAppSelection.CLASSIC), dao)
        runCurrent()

        coordinator.requestHomeModelRefresh()
        advanceTimeBy(60_000)

        assertEquals(0, dao.reads)
    }

    private fun TestScope.coordinator(
        selection: Flow<HomeAppSelection>,
        dao: FakeApplicationLogEntryDao,
    ) = HomeRankingCoordinator(
        scope = backgroundScope,
        selection = selection,
        rankingSource = ThrowingHomeAppRankingSource(),
        applicationService = applicationService(),
        applicationLogEntryDao = dao,
        additionalPackageMetadataDao = FakeAdditionalPackageMetadataDao(),
        allApps = MutableStateFlow(emptyList()),
        contacts = MutableStateFlow(emptyList()),
        launchLogVersion = MutableStateFlow(0L),
    )

    private fun applicationService(): ApplicationService {
        val context = RuntimeEnvironment.getApplication()
        return ApplicationService(
            packageManager = context.packageManager,
            launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps,
            userManager = context.getSystemService(Context.USER_SERVICE) as UserManager,
        )
    }

    /**
     * Counts the training passes that reach the launch log. [holdTraining] parks the pass inside
     * its first read so a test can issue a refresh request while a pass is genuinely in flight.
     */
    private class FakeApplicationLogEntryDao : ApplicationLogEntryDao {
        private val readsCount = AtomicInteger()
        private val finishedCount = AtomicInteger()
        private val holding = AtomicInteger()
        private val release = Semaphore(0)

        val reads get() = readsCount.get()

        fun holdTraining() {
            holding.set(1)
        }

        fun releaseHeldTraining() {
            release.release(HELD_TRAINING_PERMITS)
        }

        fun awaitReads(count: Int) = await("training reads", count, readsCount::get)

        /**
         * The coordinator records when it trained just after the log read returns and the examples
         * are built, so a test that moves [ShadowSystemClock] must let that publish land first.
         */
        fun awaitPassPublished() {
            await("finished training passes", 1, finishedCount::get)
            Thread.sleep(PUBLISH_SETTLE_MILLIS)
        }

        override fun all(): List<ApplicationLogEntry> {
            readsCount.incrementAndGet()
            if (holding.get() == 1) {
                check(release.tryAcquire(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    "the held training pass was never released"
                }
            }
            finishedCount.incrementAndGet()
            return emptyList()
        }

        override fun topAppsByScore(limit: Int, period: String): List<PackageCount> =
            unsupported("topAppsByScore")

        override fun mostUsedApps(period: String): List<PackageCount> = unsupported("mostUsedApps")

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

        private fun await(what: String, count: Int, current: () -> Int) {
            val deadline = System.nanoTime() + AWAIT_TIMEOUT_SECONDS * 1_000_000_000L
            while (current() < count) {
                if (System.nanoTime() > deadline) {
                    fail("waited for $count $what on the IO dispatcher, saw ${current()}")
                }
                Thread.sleep(10)
            }
        }

        private fun unsupported(query: String): Nothing =
            throw UnsupportedOperationException("$query is not read by HomeRankingCoordinator")
    }

    /** Every ranking read is outside this test's scope; an unexpected call must fail loudly. */
    private class ThrowingHomeAppRankingSource : HomeAppRankingSource {
        override fun topAppsByScore(limit: Int): List<HomeAppCandidate> = unsupported("topAppsByScore")

        override fun mostUsedApps(): List<HomeAppCandidate> = unsupported("mostUsedApps")

        override fun quietProfileIds(): Set<Int> = unsupported("quietProfileIds")

        private fun unsupported(query: String): Nothing =
            throw UnsupportedOperationException("$query is not read by HomeRankingCoordinator")
    }

    /** The training read only needs the hidden-from-top metadata, which the test keeps empty. */
    private class FakeAdditionalPackageMetadataDao : AdditionalPackageMetadataDao {
        override fun list(): Flow<List<AdditionalPackageMetadata>> = flowOf(emptyList())

        override fun insert(additionalPackageMetadata: AdditionalPackageMetadata) =
            unsupported("insert")

        override fun delete(additionalPackageMetadata: AdditionalPackageMetadata) =
            unsupported("delete")

        override fun upsert(updateVisibility: UpdateVisibility) = unsupported("upsert")

        override fun upsert(setUpdateAlias: UpdateAlias) = unsupported("upsert")

        private fun unsupported(query: String): Nothing =
            throw UnsupportedOperationException("$query is not read by HomeRankingCoordinator")
    }

    private companion object {
        const val AWAIT_TIMEOUT_SECONDS = 30L
        const val HELD_TRAINING_PERMITS = 64
        const val PUBLISH_SETTLE_MILLIS = 200L

        /** Real time the IO dispatcher is given to start a stray pass before one is asserted absent. */
        const val IO_SETTLE_MILLIS = 1_000L
    }
}
