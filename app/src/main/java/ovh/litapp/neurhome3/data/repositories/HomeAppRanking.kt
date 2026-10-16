package ovh.litapp.neurhome3.data.repositories

import ch.hsr.geohash.GeoHash
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.ml.HomeAppContextEncoder
import ovh.litapp.neurhome3.data.ml.HomeAppLabel
import ovh.litapp.neurhome3.data.ml.PairwiseHomeAppReranker
import ovh.litapp.neurhome3.data.ml.SupportedHomeAppCandidate
import ovh.litapp.neurhome3.data.models.MODEL_LOCATION_GEOHASH_PRECISION
import ovh.litapp.neurhome3.data.models.WifiContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

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
        if (selection != HomeAppSelection.LEARNED || model == null || !model.isUsable || position == null) {
            return classicTopApps(n)
        }

        val classic = candidatesExcludingQuietProfiles(n)
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

/**
 * Caches the ranking reads against the inputs they are computed from: the launch-log version, the
 * top-apps visibility version, and the local time bucket the query is keyed on. Both reads scan the
 * four-month launch window, so without this every home refresh re-ran the same query.
 *
 * [now] is a provider so tests can pin the bucket boundary.
 */
internal class CachedHomeAppRankingSource(
    private val delegate: HomeAppRankingSource,
    private val launchLogVersion: () -> Long,
    private val topAppsVisibilityVersion: () -> Long,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) : HomeAppRankingSource {
    private val lock = Any()
    private var scoreKey: ScoreKey? = null
    private var scoreValue: List<HomeAppCandidate>? = null
    private var mostUsedKey: MostUsedKey? = null
    private var mostUsedValue: List<HomeAppCandidate>? = null

    /**
     * The score query scores launches inside a ±20 minute window around the current local minute and
     * counts launches inside the four-month period, so both the minute and the local day it falls in
     * are part of its key.
     */
    override fun topAppsByScore(limit: Int): List<HomeAppCandidate> {
        val key = ScoreKey(
            launchLogVersion = launchLogVersion(),
            visibilityVersion = topAppsVisibilityVersion(),
            minute = now().truncatedTo(ChronoUnit.MINUTES),
            limit = limit,
        )
        synchronized(lock) {
            if (scoreKey == key) scoreValue?.let { return it }
        }
        val loaded = delegate.topAppsByScore(limit)
        synchronized(lock) {
            scoreKey = key
            scoreValue = loaded
        }
        return loaded
    }

    /** The period cutoff is a whole day, so launch counts only move when the log or the day does. */
    override fun mostUsedApps(): List<HomeAppCandidate> {
        val key = MostUsedKey(launchLogVersion = launchLogVersion(), day = now().toLocalDate())
        synchronized(lock) {
            if (mostUsedKey == key) mostUsedValue?.let { return it }
        }
        val loaded = delegate.mostUsedApps()
        synchronized(lock) {
            mostUsedKey = key
            mostUsedValue = loaded
        }
        return loaded
    }

    /** Quiet modes come from the application service's own binder cache, not from this one. */
    override fun quietProfileIds(): Set<Int> = delegate.quietProfileIds()

    private data class ScoreKey(
        val launchLogVersion: Long,
        val visibilityVersion: Long,
        val minute: LocalDateTime,
        val limit: Int,
    )

    private data class MostUsedKey(
        val launchLogVersion: Long,
        val day: LocalDate,
    )
}

/** A resolved home row, or a `null` application when the candidate no longer resolves. */
internal class CachedHomeAppRow(val application: Application?)

/**
 * Keeps the resolved row of a candidate so consecutive ranking refreshes reuse the label, activity,
 * icon, and launch-intent lookups behind it. [ApplicationService.toApplication] rebuilds all of them
 * on every call, so without this a refresh both repays the launcher binder work and compares unequal
 * to the previous list.
 *
 * Entries are dropped by [invalidatePackage] from the launcher package callbacks, which is also what
 * makes a package update pick up its new icon and a removed package disappear.
 */
internal class CachedHomeAppResolver(
    private val delegate: HomeAppResolver,
    private val rows: LauncherIconCache<CachedHomeAppRow>,
) : HomeAppResolver {
    override fun resolve(candidate: HomeAppCandidate): Application? {
        val row = rows.getOrLoad(LauncherIconKey(candidate.packageName, candidate.user, UNKNOWN_COMPONENT)) {
            CachedHomeAppRow(delegate.resolve(candidate))
        }
        return row.application?.copy(score = candidate.score)
    }

    fun invalidatePackage(packageName: String, profile: Int) {
        rows.invalidatePackage(packageName, profile)
    }
}

/**
 * Ranking candidates name one package and profile; the launched component is not known before
 * resolution, so it cannot narrow the cache key and the row cache is keyed per package and profile.
 */
private const val UNKNOWN_COMPONENT = ""
