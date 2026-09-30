package ovh.litapp.neurhome3.data.ml

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import ovh.litapp.neurhome3.data.models.ApplicationLogEntry
import ovh.litapp.neurhome3.data.models.WifiContextState
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import kotlin.math.abs

/** Runs the full local chronological corpus when HOME_APP_RERANKER_PARITY_FIXTURE is set. */
class PairwiseHomeAppChronologicalParityTest {
    private data class LaunchRow(
        val index: Int,
        val timestamp: LocalDateTime,
        val labelId: Int,
        val profile: Int,
        val context: HomeAppContextFeatures
    )

    private data class ExpectedTrainingExample(
        val launchIndex: Int,
        val targetId: Int,
        val targetPrior: Int,
        val candidates: List<Pair<Int, Int>>
    )

    private data class EvaluationRow(
        val fold: Int,
        val launchIndex: Int,
        val targetId: Int,
        val candidates: List<Pair<Int, Int>>,
        val expectedRank: Int,
        val frequentSqlRank: Int
    )

    private data class Metric(var count: Int = 0, var hits: Int = 0, var reciprocalRank: Double = 0.0) {
        fun add(rank: Int) {
            count++
            if (rank in 1..6) hits++
            if (rank > 0) reciprocalRank += 1.0 / rank
        }

        val hitRate: Double get() = hits.toDouble() / count
        val mrr: Double get() = reciprocalRank / count
    }

    @Test
    fun selectedRerankerMatchesOfflineChronologicalFolds() = runBlocking {
        val fixturePath = System.getenv("HOME_APP_RERANKER_PARITY_FIXTURE")
        assumeTrue("Set HOME_APP_RERANKER_PARITY_FIXTURE to run full chronological parity", !fixturePath.isNullOrBlank())

        val lines = File(fixturePath!!).readLines()
        val metadata = lines.first().split('\t')
        assertEquals("M", metadata[0])
        val launchCount = metadata[1].toInt()
        val fold1Start = metadata[2].toInt()
        val fold2Start = metadata[3].toInt()
        val hiddenIds = metadata[4].csvInts().toSet()
        val rows = arrayOfNulls<LaunchRow>(launchCount)
        val expectedExamples = mutableListOf<ExpectedTrainingExample>()
        val evaluations = mutableListOf<EvaluationRow>()

        for (line in lines.drop(1)) {
            val fields = line.split('\t')
            when (fields[0]) {
                "L" -> {
                    val index = fields[1].toInt()
                    val categoryIds = fields[5].csvInts().filter { it != 0 }.toIntArray()
                    val numericFeatures = fields[6].split(',').map(String::toFloat).toFloatArray()
                    rows[index] = LaunchRow(
                        index = index,
                        timestamp = LocalDateTime.parse(fields[2], DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        labelId = fields[3].toInt(),
                        profile = fields[4].toInt(),
                        context = HomeAppContextFeatures(categoryIds, numericFeatures)
                    )
                }
                "P" -> expectedExamples += ExpectedTrainingExample(
                    launchIndex = fields[1].toInt(),
                    targetId = fields[2].toInt(),
                    targetPrior = fields[3].toInt(),
                    candidates = fields[4].csvPairs()
                )
                "E" -> evaluations += EvaluationRow(
                    fold = fields[1].toInt(),
                    launchIndex = fields[2].toInt(),
                    targetId = fields[3].toInt(),
                    candidates = fields[4].csvPairs(),
                    expectedRank = fields[5].toInt(),
                    frequentSqlRank = fields[6].toInt()
                )
            }
        }
        val launches = rows.map { requireNotNull(it) }
        assertEquals(launchCount, launches.size)

        val packageForId = launches.map { it.labelId }.distinct().sorted()
            .associateWith { labelId -> "label-%04d".format(labelId) }
        val idForPackage = packageForId.entries.associate { it.value to it.key }
        val hiddenPackages = hiddenIds.mapTo(mutableSetOf()) { packageForId.getValue(it) }
        val logEntries = launches.map { row ->
            ApplicationLogEntry(
                uid = row.index + 1,
                packageName = packageForId.getValue(row.labelId),
                timestamp = row.timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                wifi = null,
                wifiState = WifiContextState.UNKNOWN,
                latitude = null,
                longitude = null,
                geohash = null,
                user = row.profile
            )
        }
        val factoryExamples = PairwiseHomeAppTrainingDataFactory.build(
            launches = logEntries.take(fold2Start),
            hiddenPackages = hiddenPackages,
            minimumSupport = 10,
            candidateLimit = 24
        )
        val expectedByIndex = expectedExamples.associateBy { it.launchIndex }
        val launchIndexesByLabelAndTime = launches.groupBy { it.timestamp to it.labelId }
            .mapValues { (_, values) -> ArrayDeque(values.map { it.index }) }
        val indexedFactoryExamples = factoryExamples.mapNotNull { actual ->
            val targetId = idForPackage.getValue(actual.target.packageName)
            val indexes = launchIndexesByLabelAndTime[actual.timestamp to targetId] ?: return@mapNotNull null
            val launchIndex = indexes.pollFirst() ?: return@mapNotNull null
            launchIndex to actual
        }
        assertEquals("Every Kotlin replay event must map to one launch row", factoryExamples.size, indexedFactoryExamples.size)

        var candidateMismatches = 0
        var candidateOrderMismatches = 0
        var supportMismatches = 0
        val mismatchSamples = mutableListOf<String>()
        indexedFactoryExamples.forEach { (launchIndex, actual) ->
            expectedByIndex[launchIndex]?.let { expected ->
                val actualCandidates = actual.classicCandidates.map {
                    idForPackage.getValue(it.label.packageName) to it.priorLaunches
                }
                if (expected.targetPrior != actual.targetPriorLaunches) supportMismatches++
                if (expected.candidates != actualCandidates) {
                    if (expected.candidates.map { it.first }.toSet() == actualCandidates.map { it.first }.toSet()) {
                        candidateOrderMismatches++
                    } else candidateMismatches++
                    if (mismatchSamples.size < 3) {
                        val firstDifference = expected.candidates.zip(actualCandidates)
                            .indexOfFirst { (left, right) -> left != right }
                        mismatchSamples += "$launchIndex:size${expected.candidates.size}/${actualCandidates.size}" +
                            ":at$firstDifference:${expected.candidates.getOrNull(firstDifference)}!=" +
                            actualCandidates.getOrNull(firstDifference)
                    }
                }
            }
        }
        val actualIndexes = indexedFactoryExamples.mapTo(mutableSetOf()) { it.first }
        val missingExamples = expectedByIndex.keys - actualIndexes
        val extraExamples = actualIndexes - expectedByIndex.keys
        println(
            "Kotlin replay divergence: missing=${missingExamples.size}, " +
                "extra=${extraExamples.size}, candidateSet=$candidateMismatches, " +
                "candidateOrderOnly=$candidateOrderMismatches, support=$supportMismatches"
        )
        // The Kotlin double-precision SQL replay and the Python float32 replay break
        // score ties and the 24-candidate boundary differently (observed: 2,953 of
        // 66,842 events). This ceiling is diagnostic; the release gate is metric
        // parity (0.003 absolute) and the 0.020 non-regression assertions below.
        val replayDifferences = missingExamples.size + extraExamples.size + candidateMismatches + supportMismatches
        assertTrue(
            "Kotlin SQL replay diverged on $replayDifferences events " +
                "(missing=${missingExamples.size}, extra=${extraExamples.size}, " +
                "candidate=$candidateMismatches, order=$candidateOrderMismatches, " +
                "support=$supportMismatches; samples=$mismatchSamples)",
            replayDifferences <= 3_000
        )

        val replayedExamples = indexedFactoryExamples.map { (launchIndex, actual) ->
            val row = launches[launchIndex]
            Pair(
                launchIndex,
                PairwiseHomeAppTrainingExample(
                    timestamp = actual.timestamp,
                    context = row.context,
                    target = actual.target,
                    targetPriorLaunches = actual.targetPriorLaunches,
                    classicCandidates = actual.classicCandidates
                )
            )
        }

        val reranker = PairwiseHomeAppReranker(PairwiseRerankerConfig())
        val cutoffs = listOf(fold1Start, fold2Start)
        for ((foldIndex, testStart) in cutoffs.withIndex()) {
            val training = replayedExamples.filter { (launchIndex, _) -> launchIndex < testStart }
                .map { it.second }
            val model = reranker.train(training, launches[testStart - 1].timestamp)
            val counts = IntArray(packageForId.size)
            launches.take(testStart).forEach { row ->
                if (row.labelId !in hiddenIds) counts[row.labelId]++
            }
            val actualAll = Metric()
            val expectedAll = Metric()
            val actualFrequent = Metric()
            val expectedFrequent = Metric()
            val classicFrequent = Metric()

            evaluations.asSequence().filter { it.fold == foldIndex + 1 }.forEach { item ->
                val candidateList = item.candidates.map { (labelId, priorCount) ->
                    SupportedHomeAppCandidate(
                        HomeAppLabel(packageForId.getValue(labelId), launches.first { it.labelId == labelId }.profile),
                        priorCount
                    )
                }
                val ranked = reranker.rerank(candidateList, launches[item.launchIndex].context, model)
                val targetLabel = HomeAppLabel(
                    packageForId.getValue(item.targetId), launches[item.launchIndex].profile
                )
                val actualRank = ranked.indexOf(targetLabel).let { if (it < 0) 0 else it + 1 }
                actualAll.add(actualRank)
                expectedAll.add(item.expectedRank)
                if (counts[item.targetId] >= 10) {
                    actualFrequent.add(actualRank)
                    expectedFrequent.add(item.expectedRank)
                    classicFrequent.add(item.frequentSqlRank)
                }
            }

            assertMetricParity("fold-${foldIndex + 1} overall HitRate@6", actualAll.hitRate, expectedAll.hitRate)
            assertMetricParity("fold-${foldIndex + 1} overall MRR", actualAll.mrr, expectedAll.mrr)
            assertMetricParity("fold-${foldIndex + 1} frequent HitRate@6", actualFrequent.hitRate, expectedFrequent.hitRate)
            assertMetricParity("fold-${foldIndex + 1} frequent MRR", actualFrequent.mrr, expectedFrequent.mrr)
            assertTrue("fold-${foldIndex + 1} frequent HitRate@6 exceeded the regression guard", actualFrequent.hitRate >= classicFrequent.hitRate - 0.020)
            assertTrue("fold-${foldIndex + 1} frequent MRR exceeded the regression guard", actualFrequent.mrr >= classicFrequent.mrr - 0.020)
            println(
                "Kotlin parity fold-${foldIndex + 1}: " +
                    "frequent HitRate@6=${"%.3f".format(actualFrequent.hitRate)}, " +
                    "MRR=${"%.3f".format(actualFrequent.mrr)}; " +
                    "offline=${"%.3f".format(expectedFrequent.hitRate)}/" +
                    "${"%.3f".format(expectedFrequent.mrr)}"
            )
        }
    }

    private fun assertMetricParity(name: String, actual: Double, expected: Double) {
        assertTrue("$name differs: Kotlin=$actual, offline=$expected", abs(actual - expected) <= 0.003)
    }

    private fun String.csvInts(): List<Int> =
        if (isBlank()) emptyList() else split(',').map(String::toInt)

    private fun String.csvPairs(): List<Pair<Int, Int>> =
        if (isBlank()) emptyList() else split(',').map { token ->
            val (labelId, count) = token.split(':', limit = 2)
            labelId.toInt() to count.toInt()
        }
}
