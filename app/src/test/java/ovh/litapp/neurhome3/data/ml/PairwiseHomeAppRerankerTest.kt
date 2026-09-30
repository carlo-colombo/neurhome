package ovh.litapp.neurhome3.data.ml

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.litapp.neurhome3.data.models.ApplicationLogEntry
import ovh.litapp.neurhome3.data.models.WifiContextState
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class PairwiseHomeAppRerankerTest {
    private val timestamp = LocalDateTime.of(2025, 4, 2, 9, 30)
    private val context = HomeAppContextEncoder.encode(
        timestamp = timestamp,
        profile = 0,
        geohash = "u4pru",
        wifiState = WifiContextState.UNKNOWN,
        wifi = null
    )

    @Test
    fun defaultConfigMatchesTheOfflineFoldOneSelection() {
        val config = PairwiseRerankerConfig()

        assertEquals(365L, config.historyDays)
        assertEquals(12, config.candidateCount)
        assertEquals(1, config.epochs)
        assertEquals(0.03f, config.learningRate)
        assertEquals(0.0001f, config.l2)
        assertEquals(10, config.minimumSupport)
    }

    @Test
    fun contextEncodingOmitsUnknownWifiAndMissingLocationAndMatchesJvmHash() {
        assertEquals(2147, HomeAppContextEncoder.stableFeatureHash("abc"))
        assertEquals(3, HomeAppContextEncoder.encode(
            timestamp = timestamp,
            profile = 0,
            geohash = null,
            wifiState = WifiContextState.UNKNOWN,
            wifi = null
        ).categoricalFeatureIds.size)
        assertEquals(4, HomeAppContextEncoder.encode(
            timestamp = timestamp,
            profile = 0,
            geohash = null,
            wifiState = WifiContextState.NO_WIFI,
            wifi = null
        ).categoricalFeatureIds.size)
        assertEquals(5, HomeAppContextEncoder.encode(
            timestamp = timestamp,
            profile = 0,
            geohash = "u4pru",
            wifiState = WifiContextState.CONNECTED,
            wifi = "synthetic-network"
        ).categoricalFeatureIds.size)
    }

    @Test
    fun pairwiseLearningChangesRankingDeterministicallyAndKeepsProfilesDistinct() = runBlocking {
        val target = HomeAppLabel("synthetic-app", 10)
        val competitor = HomeAppLabel("synthetic-app", 0)
        val candidates = listOf(
            SupportedHomeAppCandidate(competitor, priorLaunches = 30),
            SupportedHomeAppCandidate(target, priorLaunches = 12)
        )
        val examples = (0 until 30).map { index ->
            PairwiseHomeAppTrainingExample(
                timestamp = timestamp.plusDays(index.toLong()),
                context = context,
                target = target,
                targetPriorLaunches = 12 + index,
                classicCandidates = candidates
            )
        }
        val config = PairwiseRerankerConfig(
            historyDays = 365,
            candidateCount = 2,
            epochs = 3,
            learningRate = 0.08f,
            l2 = 0.0001f,
            seed = 55
        )
        val reranker = PairwiseHomeAppReranker(config)

        val firstModel = reranker.train(examples, timestamp.plusDays(30))
        val secondModel = reranker.train(examples, timestamp.plusDays(30))

        val expected = listOf(target, competitor)
        assertEquals(expected, reranker.rerank(candidates, context, firstModel))
        assertEquals(expected, reranker.rerank(candidates, context, secondModel))
    }

    @Test
    fun lowSupportLabelsAreOmittedButFrequentLabelsRemainRankable() = runBlocking {
        val rare = HomeAppLabel("synthetic-rare", 0)
        val frequentA = HomeAppLabel("synthetic-a", 0)
        val frequentB = HomeAppLabel("synthetic-b", 0)
        val reranker = PairwiseHomeAppReranker(
            PairwiseRerankerConfig(candidateCount = 6)
        )
        val emptyModel = reranker.train(emptyList())

        val result = reranker.rerank(
            listOf(
                SupportedHomeAppCandidate(rare, 2),
                SupportedHomeAppCandidate(frequentA, 20),
                SupportedHomeAppCandidate(frequentB, 10)
            ),
            context,
            emptyModel
        )

        assertEquals(listOf(frequentA, frequentB), result)
        assertTrue(rare !in result)
    }

    @Test
    fun trainingDataFactoryUsesOnlyPriorSupportedClassicCandidates() = runBlocking {
        val start = LocalDateTime.of(2025, 1, 1, 9, 0)
        val launches = (0 until 24).map { index ->
            ApplicationLogEntry(
                uid = index + 1,
                packageName = if (index < 12) "synthetic-alpha" else "synthetic-beta",
                timestamp = start.plusDays(index.toLong())
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                wifi = null,
                wifiState = WifiContextState.UNKNOWN,
                latitude = null,
                longitude = null,
                geohash = null,
                user = 0
            )
        }

        val examples = PairwiseHomeAppTrainingDataFactory.build(
            launches = launches,
            hiddenPackages = emptySet(),
            candidateLimit = 8
        )

        assertFalse(examples.isEmpty())
        assertTrue(examples.any { it.target.packageName == "synthetic-beta" })
        assertTrue(examples.all { it.targetPriorLaunches >= 10 })
        assertTrue(examples.all { example ->
            example.classicCandidates.all { it.priorLaunches >= 10 }
        })
    }
}
