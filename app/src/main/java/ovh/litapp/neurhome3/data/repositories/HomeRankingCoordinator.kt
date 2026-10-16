package ovh.litapp.neurhome3.data.repositories

import android.location.Location
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import ovh.litapp.neurhome3.ApplicationService
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.dao.AdditionalPackageMetadataDao
import ovh.litapp.neurhome3.data.dao.ApplicationLogEntryDao
import ovh.litapp.neurhome3.data.ml.PairwiseHomeAppReranker
import ovh.litapp.neurhome3.data.ml.PairwiseHomeAppTrainingDataFactory
import ovh.litapp.neurhome3.data.models.HiddenPackageType
import ovh.litapp.neurhome3.data.models.WifiContext
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Delay applied to launch-triggered model refreshes, collapsing bursts. */
private const val MODEL_REFRESH_DEBOUNCE_MS = 2_000L

/** Minimum spacing between actual trainings once [HomeAppSelection.LEARNED] is active. */
private const val MODEL_REFRESH_MIN_INTERVAL_MS = 15 * 60 * 1000L

private const val MILLIS_PER_MINUTE = 60_000L

/**
 * Serves the home top-apps ranking and owns the learned model behind it: the classic/learned
 * decision per ranking input change, the training runs and their debounce/generation bookkeeping,
 * and the cached ranking reads and resolved rows they recompute from.
 *
 * The ranking source is supplied already cached ([CachedHomeAppRankingSource]) because the plain
 * launch-count read it serves is shared with the merged application list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class HomeRankingCoordinator(
    private val scope: CoroutineScope,
    /** The user's ranking choice. Cached eagerly so refresh requests can cheaply decide whether training is warranted. */
    selection: Flow<HomeAppSelection>,
    private val rankingSource: HomeAppRankingSource,
    private val applicationService: ApplicationService,
    private val applicationLogEntryDao: ApplicationLogEntryDao,
    private val additionalPackageMetadataDao: AdditionalPackageMetadataDao,
    private val allApps: StateFlow<List<Application>>,
    private val launchLogVersion: StateFlow<Long>,
) {
    private val homeReranker = PairwiseHomeAppReranker()

    /**
     * Emits the current local minute immediately and then on every minute boundary. The ranking
     * score query is keyed on `minuteOfTheDay` and its ±20 minute score window slides with the local
     * minute, so a purely log-driven recompute would leave the classic list wrong for the rest of the
     * window; the minute boundary is the finest genuine change of the query's inputs, not a poll.
     *
     * The learned reranker's wifi and position context has no change signal to observe, so it is
     * sampled here, once per minute instead of the former 30 s. The classic ranking is not affected:
     * its value is still computed for the current minute. The right follow-up is a change-driven
     * network/location signal instead of any timer.
     */
    private val localMinutes: Flow<LocalDateTime> = flow {
        while (true) {
            val now = LocalDateTime.now()
            emit(now.truncatedTo(ChronoUnit.MINUTES))
            delay(MILLIS_PER_MINUTE - (now.second * 1000L + now.nano / 1_000_000L))
        }
    }.distinctUntilChanged()

    /**
     * Resolved home-ranking rows, so a refresh reuses the row instead of repeating the launch-intent
     * and icon binder lookups. Launcher package callbacks invalidate entries; see
     * [CachedHomeAppResolver].
     */
    private val homeAppRowCache = LauncherIconCache<CachedHomeAppRow>(LAUNCHER_ICON_CACHE_SIZE)

    private val homeAppRanking = HomeAppRanking(
        source = rankingSource,
        resolver = CachedHomeAppResolver(
            ApplicationServiceHomeAppResolver(applicationService),
            homeAppRowCache,
        ),
        reranker = homeReranker,
    )
    private val homeModel = MutableStateFlow<PairwiseHomeAppReranker.Model?>(null)

    /**
     * Current ranking choice. Cached eagerly so refresh requests can cheaply
     * decide whether training is warranted; [HomeAppSelection.CLASSIC] never trains.
     */
    private val homeAppSelection = selection
        .stateIn(scope, SharingStarted.Eagerly, HomeAppSelection.CLASSIC)

    private var modelRefreshJob: Job? = null
    private var modelRefreshDebounceJob: Job? = null
    private var lastModelTrainedAtMs = 0L

    /** Invalidates results of a superseded training so it cannot write [homeModel]. */
    private var modelTrainingGeneration = 0

    /**
     * Everything a home ranking read depends on that can change while the flow stays subscribed: the
     * installed launcher apps and their visibility metadata, the launch log, and the local minute
     * the score window is keyed on. The first value is available immediately, so the ranking never
     * waits for a tick to appear.
     */
    private val homeRankingInputs: Flow<Unit> = merge(
        allApps.map { Unit },
        launchLogVersion.map { Unit },
        localMinutes.map { Unit },
    )

    /**
     * Drops the row cached for a package the launcher just changed. Registered by the launcher-apps
     * source so a package update picks up its new icon and a removed package disappears.
     */
    fun invalidateResolvedRow(packageName: String, profile: Int) {
        homeAppRowCache.invalidatePackage(packageName, profile)
    }

    /** Classic ranking, or a safe learned reranking of the same candidates. */
    fun getTopApps(
        n: Int,
        selection: Flow<HomeAppSelection>,
        getWifiContext: () -> WifiContext,
        getPosition: () -> Location?
    ): Flow<List<Application>> = selection.flatMapLatest { choice ->
        homeModel.flatMapLatest { model ->
            homeRankingInputs
                .map {
                    homeAppRanking.topAppsOnce(
                        n = n,
                        selection = choice,
                        model = model,
                        wifi = getWifiContext(),
                        position = getPosition()?.asCoordinates(),
                    )
                }
                // The ranking reads and the resolved rows are cached, so a recompute is cheap; an
                // unchanged list still must not reach the UI, because every refresh recreates it
                // from the same candidates.
                .distinctUntilChanged()
        }
    }.flowOn(Dispatchers.IO)

    /** Refresh hook invoked after a launch landed; debounced so a burst of launches trains once. */
    fun requestHomeModelRefresh() {
        if (homeAppSelection.value != HomeAppSelection.LEARNED) return
        modelRefreshDebounceJob?.cancel()
        modelRefreshDebounceJob = scope.launch {
            delay(MODEL_REFRESH_DEBOUNCE_MS)
            refreshHomeModel()
        }
    }

    private fun refreshHomeModel(force: Boolean = false) {
        if (homeAppSelection.value != HomeAppSelection.LEARNED) return
        if (force) {
            // A forced refresh (newly selected LEARNED) supersedes any in-flight training.
            modelRefreshJob?.cancel()
        } else if (modelRefreshJob?.isActive == true) {
            // Never restart an in-flight training from a debounced refresh: the running one
            // already reads the newest launches, and restarting would re-read the whole log.
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastModelTrainedAtMs < MODEL_REFRESH_MIN_INTERVAL_MS) return
        val generation = ++modelTrainingGeneration
        modelRefreshJob = scope.launch(Dispatchers.IO) {
            val launches = applicationLogEntryDao.all()
            val hidden = additionalPackageMetadataDao.list().first()
                .filter { it.hideFrom == HiddenPackageType.TOP }
                .map { it.packageName }
                .toSet()
            val examples = PairwiseHomeAppTrainingDataFactory.build(launches, hidden)
            val trained = homeReranker.train(examples)
            // Ignore stale results if a newer refresh superseded this one or the user
            // switched back to CLASSIC meanwhile.
            if (generation == modelTrainingGeneration && homeAppSelection.value == HomeAppSelection.LEARNED) {
                homeModel.value = trained
                lastModelTrainedAtMs = SystemClock.elapsedRealtime()
            }
        }
    }

    init {
        scope.launch {
            // StateFlow already conflates, so consecutive equal selections are not re-emitted.
            homeAppSelection
                .collect { selection ->
                    when (selection) {
                        // Force a fresh model as soon as learned mode is selected.
                        HomeAppSelection.LEARNED -> refreshHomeModel(force = true)
                        HomeAppSelection.CLASSIC -> {
                            modelRefreshDebounceJob?.cancel()
                            modelRefreshJob?.cancel()
                            modelRefreshJob = null
                            modelTrainingGeneration++
                            homeModel.value = null
                        }
                    }
                }
        }
    }
}

/** Ranking input is plain coordinates; only the launch-log path keeps the raw [Location]. */
private fun Location?.asCoordinates(): Coordinates? =
    this?.let { Coordinates(latitude = it.latitude, longitude = it.longitude) }
