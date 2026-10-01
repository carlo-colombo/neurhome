package ovh.litapp.neurhome3.data.ml

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ovh.litapp.neurhome3.data.models.ApplicationLogEntry
import ovh.litapp.neurhome3.data.models.WifiContextState
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

private const val HASH_BINS = 4096
private const val CATEGORY_FEATURES = 5
private const val NUMERIC_FEATURES = 4
private const val FEATURE_COUNT = HASH_BINS + 1

data class HomeAppLabel(val packageName: String, val profile: Int) : Comparable<HomeAppLabel> {
    override fun compareTo(other: HomeAppLabel): Int =
        compareValuesBy(this, other, HomeAppLabel::packageName, HomeAppLabel::profile)
}

data class HomeAppContextFeatures(
    val categoricalFeatureIds: IntArray,
    val numericFeatures: FloatArray
) {
    init {
        require(numericFeatures.size == NUMERIC_FEATURES)
        require(categoricalFeatureIds.size <= CATEGORY_FEATURES)
        require(categoricalFeatureIds.all { it in 1 until FEATURE_COUNT })
    }
}

/** Encodes only launch-time context; missing context contributes no feature. */
object HomeAppContextEncoder {
    fun encode(entry: ApplicationLogEntry): HomeAppContextFeatures = encode(
        timestamp = parseLocalTimestamp(entry.timestamp),
        profile = entry.user,
        geohash = entry.geohash,
        wifiState = entry.wifiState,
        wifi = entry.wifi
    )

    fun encode(
        timestamp: LocalDateTime,
        profile: Int,
        geohash: String?,
        wifiState: WifiContextState,
        wifi: String?
    ): HomeAppContextFeatures {
        val minute = timestamp.hour * 60 + timestamp.minute
        val tokens = buildList {
            add("profile:$profile")
            add("weekday:${timestamp.dayOfWeek.value - 1}")
            add("half-hour:${minute / 30}")
            geohash?.take(5)?.takeIf(String::isNotBlank)?.let {
                add("geohash5:$it")
            }
            when (wifiState) {
                WifiContextState.NO_WIFI -> add("wifi:NO_WIFI")
                WifiContextState.CONNECTED -> wifi?.takeIf(String::isNotBlank)?.let {
                    add("wifi:$it")
                }
                WifiContextState.UNKNOWN -> Unit
            }
        }
        val categoryIds = tokens.map(::stableFeatureHash).distinct().sorted().toIntArray()

        val clockPhase = 2.0 * PI * minute / 1440.0
        val yearLength = if (timestamp.toLocalDate().isLeapYear) 366.0 else 365.0
        val yearPhase = 2.0 * PI * (timestamp.dayOfYear - 1) / yearLength
        val numeric = floatArrayOf(
            sin(clockPhase).toFloat(),
            cos(clockPhase).toFloat(),
            sin(yearPhase).toFloat(),
            cos(yearPhase).toFloat()
        )
        return HomeAppContextFeatures(categoryIds, numeric)
    }

    /** Uses Java String.hashCode so the offline evaluator and Android agree. */
    fun stableFeatureHash(token: String): Int =
        (token.hashCode() and Int.MAX_VALUE) % HASH_BINS + 1

    internal fun parseLocalTimestamp(value: String): LocalDateTime =
        LocalDateTime.parse(value.replace(' ', 'T'), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
}

/**
 * Fold-1-selected training hyperparameters. These values control fitting; they
 * are not app scores. The per-label/context coefficients are learned from each
 * local training history by [PairwiseHomeAppReranker.train].
 */
data class PairwiseRerankerConfig(
    val historyDays: Long = 365,
    val candidateCount: Int = 12,
    val epochs: Int = 1,
    val learningRate: Float = 0.03f,
    val l2: Float = 0.0001f,
    val minimumSupport: Int = 10,
    val seed: Long = 1701
) {
    init {
        require(historyDays > 0)
        require(candidateCount > 0)
        require(epochs > 0)
        require(learningRate > 0f)
        require(l2 >= 0f)
        require(minimumSupport > 0)
    }
}

data class SupportedHomeAppCandidate(
    val label: HomeAppLabel,
    val priorLaunches: Int
)

/** One historical launch and the classic SQL candidates available at that time. */
data class PairwiseHomeAppTrainingExample(
    val timestamp: LocalDateTime,
    val context: HomeAppContextFeatures,
    val target: HomeAppLabel,
    val targetPriorLaunches: Int,
    val classicCandidates: List<SupportedHomeAppCandidate>,
    val sampleWeight: Float = 1f
)

/**
 * Deterministic local pairwise logistic ranker. Callers build examples from
 * launch history and classic SQL rankings; fitting always runs on Default.
 */
class PairwiseHomeAppReranker(
    val config: PairwiseRerankerConfig = PairwiseRerankerConfig()
) {
    class Model internal constructor(
        internal val labels: List<HomeAppLabel>,
        internal val categoricalWeights: Array<FloatArray>,
        internal val numericWeights: Array<FloatArray>,
        internal val biases: FloatArray,
        internal val classicRankWeight: Float,
        val config: PairwiseRerankerConfig
    ) {
        internal val labelIds = labels.withIndex().associate { it.value to it.index }

        val isUsable: Boolean get() = labels.isNotEmpty()
    }

    suspend fun train(
        examples: List<PairwiseHomeAppTrainingExample>,
        trainingCutoff: LocalDateTime = examples.maxOfOrNull { it.timestamp }
            ?: LocalDateTime.MIN
    ): Model = withContext(Dispatchers.Default) {
        trainBlocking(examples, trainingCutoff)
    }

    private fun trainBlocking(
        examples: List<PairwiseHomeAppTrainingExample>,
        trainingCutoff: LocalDateTime
    ): Model {
        if (examples.isEmpty()) {
            return Model(
                labels = emptyList(),
                categoricalWeights = emptyArray(),
                numericWeights = emptyArray(),
                biases = FloatArray(0),
                classicRankWeight = 0f,
                config = config
            )
        }
        val windowStart = trainingCutoff.minusDays(config.historyDays)
        val usableExamples = examples.asSequence()
            .filter { it.timestamp <= trainingCutoff && it.timestamp >= windowStart }
            .filter { it.targetPriorLaunches >= config.minimumSupport }
            .map { example ->
                val candidates = example.classicCandidates
                    .asSequence()
                    .filter { it.priorLaunches >= config.minimumSupport }
                    .distinctBy { it.label }
                    .take(config.candidateCount)
                    .toList()
                example to candidates
            }
            .filter { (example, candidates) ->
                candidates.size > 1 && candidates.any { it.label == example.target }
            }
            .toList()
        val labels = usableExamples
            .flatMap { (_, candidates) -> candidates.map { it.label } }
            .distinct()
            .sorted()
        val labelIds = labels.withIndex().associate { it.value to it.index }
        val categoricalWeights = Array(labels.size) { FloatArray(FEATURE_COUNT) }
        val numericWeights = Array(labels.size) { FloatArray(NUMERIC_FEATURES) }
        val biases = FloatArray(labels.size)
        var rankWeight = 0f

        if (usableExamples.isNotEmpty()) {
            val random = Random(config.seed)
            repeat(config.epochs) {
                val order = usableExamples.indices.toMutableList()
                java.util.Collections.shuffle(order, random)
                for (sampleIndex in order) {
                    val (example, candidates) = usableExamples[sampleIndex]
                    val candidateIds = candidates.map { labelIds.getValue(it.label) }
                    val targetPosition = candidates.indexOfFirst { it.label == example.target }
                    val targetId = candidateIds[targetPosition]
                    val featureIds = example.context.categoricalFeatureIds
                        .distinct()
                        .sorted()
                    val numericFeatures = example.context.numericFeatures
                    val rankFeatures = FloatArray(candidateIds.size) { 1f / (it + 1f) }
                    val scores = FloatArray(candidateIds.size)

                    for (position in candidateIds.indices) {
                        val labelId = candidateIds[position]
                        var score = biases[labelId] + rankWeight * rankFeatures[position]
                        for (featureId in featureIds) {
                            score += categoricalWeights[labelId][featureId]
                        }
                        for (featureIndex in 0 until NUMERIC_FEATURES) {
                            score += numericWeights[labelId][featureIndex] *
                                numericFeatures[featureIndex]
                        }
                        scores[position] = score
                    }

                    val rivalPositions = candidateIds.indices.filter { it != targetPosition }
                    val pairWeights = FloatArray(rivalPositions.size)
                    var totalWeight = 0f
                    for (rivalIndex in rivalPositions.indices) {
                        val difference = scores[targetPosition] - scores[rivalPositions[rivalIndex]]
                        val probability = 1f / (1f + exp(difference.coerceIn(-30f, 30f)))
                        val weight = probability * example.sampleWeight / rivalPositions.size
                        pairWeights[rivalIndex] = weight
                        totalWeight += weight
                    }

                    for (featureId in featureIds) {
                        val targetValue = categoricalWeights[targetId][featureId]
                        categoricalWeights[targetId][featureId] += config.learningRate *
                            (totalWeight - config.l2 * targetValue)
                        for (rivalIndex in rivalPositions.indices) {
                            val rivalId = candidateIds[rivalPositions[rivalIndex]]
                            val rivalValue = categoricalWeights[rivalId][featureId]
                            categoricalWeights[rivalId][featureId] += config.learningRate *
                                (-pairWeights[rivalIndex] - config.l2 * rivalValue)
                        }
                    }

                    for (featureIndex in 0 until NUMERIC_FEATURES) {
                        val featureValue = numericFeatures[featureIndex]
                        val targetValue = numericWeights[targetId][featureIndex]
                        numericWeights[targetId][featureIndex] += config.learningRate *
                            (totalWeight * featureValue - config.l2 * targetValue)
                        for (rivalIndex in rivalPositions.indices) {
                            val rivalId = candidateIds[rivalPositions[rivalIndex]]
                            val rivalValue = numericWeights[rivalId][featureIndex]
                            numericWeights[rivalId][featureIndex] += config.learningRate *
                                (-pairWeights[rivalIndex] * featureValue - config.l2 * rivalValue)
                        }
                    }

                    biases[targetId] += config.learningRate *
                        (totalWeight - config.l2 * biases[targetId])
                    for (rivalIndex in rivalPositions.indices) {
                        val rivalId = candidateIds[rivalPositions[rivalIndex]]
                        biases[rivalId] += config.learningRate *
                            (-pairWeights[rivalIndex] - config.l2 * biases[rivalId])
                    }

                    var rankGradient = 0f
                    for (rivalIndex in rivalPositions.indices) {
                        rankGradient += pairWeights[rivalIndex] *
                            (rankFeatures[targetPosition] - rankFeatures[rivalPositions[rivalIndex]])
                    }
                    rankWeight += config.learningRate *
                        (rankGradient - config.l2 * rankWeight)
                }
            }
        }

        return Model(labels, categoricalWeights, numericWeights, biases, rankWeight, config)
    }

    /**
     * Reranks only frequent positive-score SQL candidates. Lower-support apps
     * remain available elsewhere in the launcher but are omitted here.
     */
    fun rerank(
        classicCandidates: List<SupportedHomeAppCandidate>,
        context: HomeAppContextFeatures,
        model: Model
    ): List<HomeAppLabel> {
        val modelConfig = model.config
        val frequent = classicCandidates
            .asSequence()
            .filter { it.priorLaunches >= modelConfig.minimumSupport }
            .distinctBy { it.label }
            .toList()
        val selected = frequent.take(modelConfig.candidateCount)
        val remaining = frequent.drop(modelConfig.candidateCount)
        if (selected.size < 2) return frequent.map { it.label }

        val featureIds = context.categoricalFeatureIds.distinct().sorted()
        val scored = selected.mapIndexed { position, candidate ->
            val labelId = model.labelIds[candidate.label]
            var score = model.classicRankWeight / (position + 1f)
            if (labelId != null) {
                score += model.biases[labelId]
                for (featureId in featureIds) {
                    score += model.categoricalWeights[labelId][featureId]
                }
                for (featureIndex in 0 until NUMERIC_FEATURES) {
                    score += model.numericWeights[labelId][featureIndex] *
                        context.numericFeatures[featureIndex]
                }
            }
            candidate.label to score
        }
        val reranked = scored
            .withIndex()
            .sortedWith(compareBy<IndexedValue<Pair<HomeAppLabel, Float>>> { -it.value.second }
                .thenBy { it.index })
            .map { it.value.first }
        return reranked + remaining.map { it.label }
    }
}
