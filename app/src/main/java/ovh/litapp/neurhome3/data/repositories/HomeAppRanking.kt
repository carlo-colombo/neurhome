package ovh.litapp.neurhome3.data.repositories

import ch.hsr.geohash.GeoHash
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.ml.HomeAppContextEncoder
import ovh.litapp.neurhome3.data.ml.HomeAppLabel
import ovh.litapp.neurhome3.data.ml.PairwiseHomeAppReranker
import ovh.litapp.neurhome3.data.ml.SupportedHomeAppCandidate
import ovh.litapp.neurhome3.data.models.MODEL_LOCATION_GEOHASH_PRECISION
import ovh.litapp.neurhome3.data.models.WifiContext
import java.time.LocalDateTime

/**
 * Home candidates fetched before quiet-profile and uninstalled-app filtering, which can drop
 * many rows; over-fetch so the visible list still reaches [n].
 */
internal fun homeCandidateLimit(n: Int): Int = n * 8 + 16

/** One launch-score row for a package/profile, the shape the DAO ranking query returns. */
data class HomeAppCandidate(
    val packageName: String,
    val user: Int,
    val score: Double,
)

/** Latitude/longitude pair; keeps the ranking free of `android.location.Location`. */
internal data class Coordinates(
    val latitude: Double,
    val longitude: Double,
)

/** Turns a ranked candidate back into the renderable application row. */
internal fun interface HomeAppResolver {
    fun resolve(candidate: HomeAppCandidate): Application?
}

/**
 * The ranking's read inputs: launch-score candidates, launch counts, and the profiles whose
 * quiet mode hides them. The app implements this over Room and the launcher services; tests
 * implement it with fakes.
 */
interface HomeAppRankingSource {
    /** Candidates in launch-score order, capped at [limit]. */
    fun topAppsByScore(limit: Int): List<HomeAppCandidate>

    /** Launch counts per package/profile within the statistics period. */
    fun mostUsedApps(): List<HomeAppCandidate>

    /** Profiles whose quiet mode hides their applications from the home list. */
    fun quietProfileIds(): Set<Int>
}

/**
 * Home top-apps ranking: the classic launch-score order, or a learned reranking of the same
 * candidates. Deliberately free of Android and Room types so it can be driven by fakes on the
 * JVM.
 */
internal class HomeAppRanking(
    private val source: HomeAppRankingSource,
    private val resolver: HomeAppResolver,
    private val reranker: PairwiseHomeAppReranker,
) {
    /** Classic ranking: quiet-profile rows dropped, resolved applications capped at [n]. */
    fun classicTopApps(n: Int): List<Application> =
        candidatesExcludingQuietProfiles(n).asSequence().mapNotNull(resolver::resolve).take(n).toList()

    /** Classic ranking, or a safe learned reranking of the same candidates. */
    fun topAppsOnce(
        n: Int,
        selection: HomeAppSelection,
        model: PairwiseHomeAppReranker.Model?,
        wifi: WifiContext,
        position: Coordinates?,
    ): List<Application> {
        val classic = candidatesExcludingQuietProfiles(n)
        if (selection != HomeAppSelection.LEARNED || model == null || !model.isUsable || position == null) {
            return classic.asSequence().mapNotNull(resolver::resolve).take(n).toList()
        }

        val counts = source.mostUsedApps().associate { (it.packageName to it.user) to it.score.toInt() }
        val candidates = classic.map {
            SupportedHomeAppCandidate(HomeAppLabel(it.packageName, it.user), counts[it.packageName to it.user] ?: 0)
        }
        val geohash = runCatching {
            GeoHash.withCharacterPrecision(position.latitude, position.longitude, MODEL_LOCATION_GEOHASH_PRECISION)
                .toBase32()
        }.getOrNull()
        val context = HomeAppContextEncoder.encode(
            timestamp = LocalDateTime.now(),
            profile = 0,
            geohash = geohash,
            wifiState = wifi.state,
            wifi = wifi.ssid
        )
        val ranked = reranker.rerank(candidates, context, model)
        val byLabel = classic.associateBy { HomeAppLabel(it.packageName, it.user) }
        return ranked.asSequence().mapNotNull { label ->
            byLabel[label]?.let(resolver::resolve)
        }.take(n).toList().ifEmpty {
            classic.asSequence().mapNotNull(resolver::resolve).take(n).toList()
        }
    }

    private fun candidatesExcludingQuietProfiles(n: Int): List<HomeAppCandidate> {
        val quietProfileIds = source.quietProfileIds()
        return source.topAppsByScore(homeCandidateLimit(n)).filter { it.user !in quietProfileIds }
    }
}
