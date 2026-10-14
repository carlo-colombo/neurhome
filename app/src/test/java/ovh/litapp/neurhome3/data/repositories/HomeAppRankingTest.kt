package ovh.litapp.neurhome3.data.repositories

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.ml.HomeAppContextEncoder
import ovh.litapp.neurhome3.data.ml.HomeAppLabel
import ovh.litapp.neurhome3.data.ml.PairwiseHomeAppReranker
import ovh.litapp.neurhome3.data.ml.PairwiseHomeAppTrainingExample
import ovh.litapp.neurhome3.data.ml.SupportedHomeAppCandidate
import ovh.litapp.neurhome3.data.models.WifiContext
import ovh.litapp.neurhome3.data.models.WifiContextState
import java.time.LocalDateTime

/**
 * Drives [HomeAppRanking] with fakes only: the ranking/filtering path needs neither Android
 * services nor a Room database.
 */
class HomeAppRankingTest {
    private val timestamp = LocalDateTime.of(2025, 4, 2, 9, 30)

    @Test
    fun classicRankingDropsQuietProfilesAndOverFetchesBeforeCapping() {
        val source = FakeHomeAppRankingSource(
            candidates = listOf(
                HomeAppCandidate("first", user = 0, score = 3.0),
                HomeAppCandidate("quiet", user = 10, score = 2.5),
                HomeAppCandidate("second", user = 0, score = 2.0),
            ),
            quietProfiles = setOf(10),
        )
        val resolver = FakeHomeAppResolver()

        val ranked = ranking(source, resolver).classicTopApps(n = 2)

        assertEquals(listOf("first", "second"), ranked.map { it.packageName })
        // Pin the over-fetch formula to a literal so a regression cannot satisfy the assertion
        // by changing both the helper and the expectation.
        assertEquals(32, homeCandidateLimit(2))
        assertEquals(64, homeCandidateLimit(6))
        assertEquals(listOf(32), source.requestedLimits)
        // Quiet profiles are dropped before resolution, so no launcher lookup happens for them.
        assertEquals(listOf("first", "second"), resolver.resolvedPackages)
    }

    @Test
    fun learnedSelectionFallsBackToClassicWithoutAUsableModel() = runBlocking {
        val source = highSupportSource()
        val reranker = PairwiseHomeAppReranker()
        val ranking = ranking(source, FakeHomeAppResolver(), reranker)
        val position = Coordinates(latitude = 48.85, longitude = 2.35)

        val classicOrder = ranking.classicTopApps(n = 2).map { it.packageName }

        assertEquals(
            classicOrder,
            ranking.topAppsOnce(2, HomeAppSelection.LEARNED, null, WifiContext.UNKNOWN, position)
                .map { it.packageName }
        )
        assertEquals(
            classicOrder,
            ranking.topAppsOnce(
                2, HomeAppSelection.LEARNED, reranker.train(emptyList()), WifiContext.UNKNOWN, position
            ).map { it.packageName }
        )
        assertEquals(
            classicOrder,
            ranking.topAppsOnce(
                2, HomeAppSelection.LEARNED, usableModel(reranker), WifiContext.UNKNOWN, position = null
            ).map { it.packageName }
        )
    }

    @Test
    fun learnedSelectionFallsBackToClassicWhenNoCandidateHasEnoughSupport() = runBlocking {
        val source = FakeHomeAppRankingSource(
            candidates = listOf(
                HomeAppCandidate("first", user = 0, score = 3.0),
                HomeAppCandidate("second", user = 0, score = 2.0),
            ),
            launchCounts = listOf(
                HomeAppCandidate("first", user = 0, score = 4.0),
                HomeAppCandidate("second", user = 0, score = 3.0),
            ),
        )
        val reranker = PairwiseHomeAppReranker()
        val ranking = ranking(source, FakeHomeAppResolver(), reranker)

        val ranked = ranking.topAppsOnce(
            2,
            HomeAppSelection.LEARNED,
            usableModel(reranker),
            WifiContext.UNKNOWN,
            Coordinates(latitude = 48.85, longitude = 2.35),
        )

        // Below the reranker's minimum support the learned order is empty; the classic order
        // must survive instead of emptying the Home list.
        assertTrue(source.mostUsedAppsRequests > 0)
        assertEquals(listOf("first", "second"), ranked.map { it.packageName })
    }

    @Test
    fun learnedSelectionOmitsCandidatesBelowTheRerankerSupportFloor() = runBlocking {
        val source = FakeHomeAppRankingSource(
            candidates = listOf(
                HomeAppCandidate("competitor", user = 0, score = 3.0),
                HomeAppCandidate("target", user = 0, score = 2.0),
                HomeAppCandidate("rare", user = 0, score = 1.0),
            ),
            launchCounts = listOf(
                HomeAppCandidate("competitor", user = 0, score = 30.0),
                HomeAppCandidate("target", user = 0, score = 12.0),
                HomeAppCandidate("rare", user = 0, score = 3.0),
            ),
        )
        val reranker = PairwiseHomeAppReranker()
        val ranking = ranking(source, FakeHomeAppResolver(), reranker)

        val classic = ranking.classicTopApps(n = 3)
        val learned = ranking.topAppsOnce(
            3,
            HomeAppSelection.LEARNED,
            usableModel(reranker),
            WifiContext.UNKNOWN,
            Coordinates(latitude = 48.85, longitude = 2.35),
        )

        assertEquals(listOf("competitor", "target", "rare"), classic.map { it.packageName })
        assertTrue(source.mostUsedAppsRequests > 0)
        // The learned order is a subset of the classic candidates: it never invents an app, and
        // it drops the one below the reranker's minimum support.
        assertEquals(setOf("competitor", "target"), learned.map { it.packageName }.toSet())
    }

    @Test
    fun tagFilteringRunsOnTheRankedOutput() {
        val source = FakeHomeAppRankingSource(
            candidates = listOf(
                HomeAppCandidate("first", user = 0, score = 3.0),
                HomeAppCandidate("second", user = 0, score = 2.0),
            ),
        )
        val resolver = FakeHomeAppResolver(tagsByPackage = mapOf("first" to listOf("Work")))
        val ranking = ranking(source, resolver)

        val ranked = ranking.classicTopApps(n = 2)

        assertEquals(listOf("first"), filterApplicationsForTag(ranked, "Work").map { it.packageName })
        assertEquals(listOf("second"), filterApplicationsForTag(ranked, null).map { it.packageName })
    }

    private fun ranking(
        source: HomeAppRankingSource,
        resolver: HomeAppResolver,
        reranker: PairwiseHomeAppReranker = PairwiseHomeAppReranker(),
    ) = HomeAppRanking(source = source, resolver = resolver, reranker = reranker)

    private fun highSupportSource() = FakeHomeAppRankingSource(
        candidates = listOf(
            HomeAppCandidate("competitor", user = 0, score = 3.0),
            HomeAppCandidate("target", user = 0, score = 2.0),
        ),
        launchCounts = listOf(
            HomeAppCandidate("competitor", user = 0, score = 30.0),
            HomeAppCandidate("target", user = 0, score = 12.0),
        ),
    )

    /** Mirrors the reranker test's deterministic fit: a usable model over both candidates. */
    private suspend fun usableModel(reranker: PairwiseHomeAppReranker): PairwiseHomeAppReranker.Model {
        val target = HomeAppLabel("target", 0)
        val competitor = HomeAppLabel("competitor", 0)
        val candidates = listOf(
            SupportedHomeAppCandidate(competitor, priorLaunches = 30),
            SupportedHomeAppCandidate(target, priorLaunches = 12),
        )
        val context = HomeAppContextEncoder.encode(
            timestamp = timestamp,
            profile = 0,
            geohash = "u4pru",
            wifiState = WifiContextState.UNKNOWN,
            wifi = null,
        )
        val examples = (0 until 30).map { index ->
            PairwiseHomeAppTrainingExample(
                timestamp = timestamp.plusDays(index.toLong()),
                context = context,
                target = target,
                targetPriorLaunches = 12 + index,
                classicCandidates = candidates,
            )
        }
        return reranker.train(examples, timestamp.plusDays(30))
    }
}

private class FakeHomeAppRankingSource(
    private val candidates: List<HomeAppCandidate> = emptyList(),
    private val launchCounts: List<HomeAppCandidate> = emptyList(),
    private val quietProfiles: Set<Int> = emptySet(),
) : HomeAppRankingSource {
    val requestedLimits = mutableListOf<Int>()
    var mostUsedAppsRequests = 0

    override fun topAppsByScore(limit: Int): List<HomeAppCandidate> {
        requestedLimits += limit
        return candidates
    }

    override fun mostUsedApps(): List<HomeAppCandidate> {
        mostUsedAppsRequests++
        return launchCounts
    }

    override fun quietProfileIds(): Set<Int> = quietProfiles
}

private class FakeHomeAppResolver(
    private val tagsByPackage: Map<String, List<String>> = emptyMap(),
) : HomeAppResolver {
    val resolvedPackages = mutableListOf<String>()

    override fun resolve(candidate: HomeAppCandidate): Application? {
        resolvedPackages += candidate.packageName
        return Application(
            label = candidate.packageName,
            packageName = candidate.packageName,
            icon = null,
            score = candidate.score,
            tags = tagsByPackage[candidate.packageName].orEmpty(),
        )
    }
}
