package ovh.litapp.neurhome3.data.repositories

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.location.Location
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.UserHandle
import android.util.Log
import ch.hsr.geohash.GeoHash
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import ovh.litapp.neurhome3.ApplicationService
import ovh.litapp.neurhome3.application.NeurhomeApplication
import ovh.litapp.neurhome3.data.AppDatabase
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.ApplicationVisibility
import ovh.litapp.neurhome3.data.dao.AdditionalPackageMetadataDao
import ovh.litapp.neurhome3.data.dao.ApplicationLogEntryDao
import ovh.litapp.neurhome3.data.dao.ContactsDAO
import ovh.litapp.neurhome3.data.dao.UpdateAlias
import ovh.litapp.neurhome3.data.dao.UpdateVisibility
import ovh.litapp.neurhome3.data.models.ApplicationLogEntry
import ovh.litapp.neurhome3.data.models.ApplicationTag
import ovh.litapp.neurhome3.data.models.HiddenPackageType
import ovh.litapp.neurhome3.data.models.WifiContext
import ovh.litapp.neurhome3.data.ml.PairwiseHomeAppReranker
import ovh.litapp.neurhome3.data.ml.PairwiseHomeAppTrainingDataFactory
import ovh.litapp.neurhome3.data.models.MODEL_LOCATION_GEOHASH_PRECISION
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private const val TAG = "NeurhomeRepository"
private const val LAUNCHER_ICON_CACHE_SIZE = 256

/** Delay applied to launch-triggered model refreshes, collapsing bursts. */
private const val MODEL_REFRESH_DEBOUNCE_MS = 2_000L

/** Minimum spacing between actual trainings once [HomeAppSelection.LEARNED] is active. */
private const val MODEL_REFRESH_MIN_INTERVAL_MS = 15 * 60 * 1000L

/** Statistics simulation cap; keeps the heavy self-join query from materialising every row. */
private const val SIMULATED_TOP_APPS_SQL_LIMIT = 100

private const val MILLIS_PER_MINUTE = 60_000L

/**
 * Groups tag assignments by application and orders each application's tags with the shared
 * locale-independent label order ([String.CASE_INSENSITIVE_ORDER]), matching every other
 * application and tag sort path.
 */
internal fun tagNamesByApplication(assignments: List<ApplicationTag>): Map<Pair<String, Int>, List<String>> =
    assignments.groupBy { it.packageName to it.profile }
        .mapValues { (_, values) -> values.map { it.tagName }.sortedWith(String.CASE_INSENSITIVE_ORDER) }

private fun Application.withTags(
    tagsByApplication: Map<Pair<String, Int>, List<String>>,
): Application = copy(
    tags = tagsByApplication[packageName to (appInfo?.user?.hashCode() ?: 0)].orEmpty()
)

/**
 * The single application label order: [String.CASE_INSENSITIVE_ORDER], shared by the home,
 * tag, and contact-merged list paths.
 */
internal fun List<Application>.sortedByLabelCaseInsensitive(): List<Application> =
    sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

internal fun filterApplicationsForTag(
    applications: List<Application>,
    tagName: String?,
): List<Application> = applications.filter { app ->
    if (tagName == null) app.tags.isEmpty() else tagName in app.tags
}.sortedByLabelCaseInsensitive()

@OptIn(ExperimentalCoroutinesApi::class)
class NeurhomeRepository(
    private val applicationLogEntryDao: ApplicationLogEntryDao,
    private val additionalPackageMetadataDao: AdditionalPackageMetadataDao,
    private val applicationService: ApplicationService,
    private val contactsDAO: ContactsDAO,
    val application: NeurhomeApplication,
    val database: AppDatabase,
    val launcherApps: LauncherApps,
    private val homeAppRankingSource: HomeAppRankingSource,
    private val tagRepository: TagRepository,
    /** Resolves the shareable content URI for the exported database. */
    private val databaseUriProvider: (Context) -> Uri,
) {
    // SupervisorJob so one failed write/refresh does not cancel every later one.
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val launcherIconCache = LauncherIconCache<Drawable>(LAUNCHER_ICON_CACHE_SIZE)
    private val homeReranker = PairwiseHomeAppReranker()

    /**
     * Launch-log version, bumped on every insert. The ranking reads are computed from the log, so
     * they are cached against this version and recomputed when a launch lands instead of on a timer.
     */
    private val launchLogVersion = MutableStateFlow(0L)

    /**
     * Top-apps visibility version, bumped when the visibility metadata changes; the ranking score
     * query filters the packages hidden from top, so its cached read has to follow it.
     */
    private val topAppsVisibilityVersion = MutableStateFlow(0L)

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
     * Emits the current local date immediately and then at every midnight: the ranking queries
     * select their four-month period with `date('now')`, so the cutoff is a daily input.
     */
    private val localDays: Flow<LocalDate> = flow {
        while (true) {
            val now = LocalDateTime.now()
            emit(now.toLocalDate())
            delay(
                Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay())
                    .toMillis()
                    .coerceAtLeast(1L)
            )
        }
    }.distinctUntilChanged()

    /**
     * Resolved home-ranking rows, so a refresh reuses the row instead of repeating the launch-intent
     * and icon binder lookups. Launcher package callbacks invalidate entries; see
     * [CachedHomeAppResolver].
     */
    private val homeAppRowCache = LauncherIconCache<CachedHomeAppRow>(LAUNCHER_ICON_CACHE_SIZE)

    private val rankingSource = CachedHomeAppRankingSource(
        delegate = homeAppRankingSource,
        launchLogVersion = { launchLogVersion.value },
        topAppsVisibilityVersion = { topAppsVisibilityVersion.value },
    )

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
     * decide whether training is warranted; [CLASSIC] never trains.
     */
    private val homeAppSelection = application.settingsRepository.homeAppSelection
        .stateIn(coroutineScope, SharingStarted.Eagerly, HomeAppSelection.CLASSIC)

    private var modelRefreshJob: kotlinx.coroutines.Job? = null
    private var modelRefreshDebounceJob: kotlinx.coroutines.Job? = null
    private var lastModelTrainedAtMs = 0L

    /**
     * Stops all work started against the current database. A repository is not reusable
     * after the database file is replaced (`NeurhomeApplication.rebindDataLayer` builds a
     * new one), and the eager flows above would otherwise keep the closed database alive.
     */
    fun close() {
        coroutineScope.cancel()
    }

    /** Invalidates results of a superseded training so it cannot write [homeModel]. */
    private var modelTrainingGeneration = 0

    private val ticker = flow {
        while (true) {
            emit(42)
            delay(Duration.ofMinutes(5).toMillis())
        }
    }
    private val contacts =
        combine(ticker, application.settingsRepository.showStarredContacts) { _, showContacts ->
            if (!showContacts || !application.checkPermission(Manifest.permission.READ_CONTACTS)) {
                return@combine listOf()
            }

            contactsDAO.getStarredContacts()
        }
            // The contacts provider has no invalidation signal here, so the list is re-read on the
            // 5 minute tick; an unchanged list must not re-merge the whole application list.
            .distinctUntilChanged()

    private val listAllLauncherApps = callbackFlow {
        fun refresh() {
            // A work profile added, removed, or turned off/on reaches us as its packages becoming
            // (un)available for that user, so the cached profile/quiet-mode state is dropped here
            // instead of being re-read by every home refresh.
            applicationService.invalidateProfileState()
            trySend(launcherApps.profiles.flatMap { launcherApps.getActivityList(null, it) })
        }

        fun invalidate(packageName: String, user: UserHandle) {
            launcherIconCache.invalidatePackage(packageName, user.hashCode())
            homeAppRowCache.invalidatePackage(packageName, user.hashCode())
        }

        refresh()

        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String, user: UserHandle) {
                invalidate(packageName, user)
                refresh()
            }

            override fun onPackageRemoved(packageName: String, user: UserHandle) {
                invalidate(packageName, user)
                refresh()
            }

            override fun onPackageChanged(packageName: String, user: UserHandle) {
                invalidate(packageName, user)
                refresh()
            }

            override fun onPackagesAvailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean
            ) {
                packageNames.forEach { invalidate(it, user) }
                refresh()
            }

            override fun onPackagesUnavailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean
            ) {
                packageNames.forEach { invalidate(it, user) }
                refresh()
            }
        }

        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))

        awaitClose {
            launcherApps.unregisterCallback(callback)
        }
    }.flowOn(Dispatchers.IO)

    private val allApps = combine(
        listAllLauncherApps,
        // Room re-emits the whole table on any invalidation, so identical metadata would otherwise
        // re-map and re-allocate every launcher row for nothing.
        additionalPackageMetadataDao.list().distinctUntilChanged()
    ) { launcherApps, metadata ->
        val metadataMap = metadata.associateBy { it.packageName to it.user }

        launcherApps.map { app ->
            val packageName = app.activityInfo.packageName
            val additionalPackageMetadata = metadataMap[packageName to app.user.hashCode()]

            Application(
                label = app.label.toString(),
                packageName = packageName,
                icon = launcherIconCache.getOrLoad(
                    LauncherIconKey(
                        packageName = packageName,
                        profile = app.user.hashCode(),
                        componentName = app.componentName.flattenToString(),
                    )
                ) { app.getBadgedIcon(0) },
                visibility = when (additionalPackageMetadata?.hideFrom) {
                    HiddenPackageType.TOP -> ApplicationVisibility.HIDDEN_FROM_TOP
                    HiddenPackageType.FILTERED -> ApplicationVisibility.HIDDEN_FROM_FILTERED
                    else -> ApplicationVisibility.VISIBLE
                },
                appInfo = app,
                intent = null,
                alias = additionalPackageMetadata?.alias ?: ""
            )
        }
    }.flowOn(Dispatchers.IO)
        .stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())

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
     * Tag assignments shared by both merged lists. Room re-emits the whole table on any
     * invalidation, so a write that left the assignments unchanged stops here instead of re-mapping
     * and re-sorting every application row.
     */
    private val tagAssignments: Flow<List<ApplicationTag>> =
        tagRepository.assignments.distinctUntilChanged()

    /**
     * Single shared, label-sorted list every tag/uncategorized page filters from. Sharing the
     * combine means a new subscriber reuses the current list instead of re-mapping and re-sorting
     * every application; [SharingStarted.WhileSubscribed] keeps it alive while any page is shown
     * and replays the last list afterwards, so returning to a category page never flashes empty.
     */
    private val applicationsWithTags: StateFlow<List<Application>> =
        combine(allApps, tagAssignments) { apps, assignments ->
            val tagsByApplication = tagNamesByApplication(assignments)
            apps.map { app ->
                app.withTags(tagsByApplication)
            }.sortedByLabelCaseInsensitive()
        }.flowOn(Dispatchers.IO)
            .stateIn(
                coroutineScope,
                // Never drop the cached list: a page re-entering the pager must replay it, not emptyList().
                SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = Long.MAX_VALUE),
                emptyList(),
            )

    /**
     * Launch counts per package for the statistics period. Recomputed when the launch log changes or
     * the local day rolls over (the period cutoff moves) instead of on a 5 minute timer.
     */
    private val packageFrequency: Flow<Map<String, Double>> = merge(
        launchLogVersion.map { Unit },
        localDays.map { Unit },
    ).map {
        rankingSource.mostUsedApps().associate { it.packageName to it.score }
    }.distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    val applicationAndContacts: Flow<List<Application>> =
        combine(
            packageFrequency,
            contacts,
            allApps,
            tagAssignments
        ) { packageFrequency, contacts, allApps, assignments ->
            val tagsByApplication = tagNamesByApplication(assignments)
            (contacts.map { a ->
                a.copy(score = packageFrequency[a.intent?.data.toString()] ?: 0.0)
            } + allApps.map { a ->
                a.copy(
                    score = packageFrequency[a.packageName] ?: 0.0,
                ).withTags(tagsByApplication)
            }).sortedByLabelCaseInsensitive()
        }.distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    fun applicationsForTag(tagName: String?): Flow<List<Application>> = applicationsWithTags.map { apps ->
        filterApplicationsForTag(apps, tagName)
    }

    fun setTags(application: Application, tags: Set<String>) {
        coroutineScope.launch(Dispatchers.IO) {
            tagRepository.setTags(
                application.packageName,
                application.appInfo?.user?.hashCode() ?: 0,
                tags
            )
        }
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
        modelRefreshJob = coroutineScope.launch(Dispatchers.IO) {
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

    /** Debounces launch-triggered refreshes so a burst of launches trains once. */
    private fun requestHomeModelRefresh() {
        if (homeAppSelection.value != HomeAppSelection.LEARNED) return
        modelRefreshDebounceJob?.cancel()
        modelRefreshDebounceJob = coroutineScope.launch {
            delay(MODEL_REFRESH_DEBOUNCE_MS)
            refreshHomeModel()
        }
    }

    init {
        coroutineScope.launch {
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

    fun logLaunch(
        packageName: String,
        user: Int,
        wifiContext: WifiContext,
        position: Location?,
        query: String? = null
    ) {
        Log.d(TAG, "logLaunch: $packageName:${wifiContext.state}:hasLocation=${position != null}")

        coroutineScope.launch(Dispatchers.IO) {
            applicationLogEntryDao.insert(
                ApplicationLogEntry(
                    packageName = packageName,
                    timestamp = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault())
                        .format(Instant.now()),
                    wifi = wifiContext.ssid,
                    wifiState = wifiContext.state,
                    latitude = position?.latitude,
                    longitude = position?.longitude,
                    geohash = if (position != null) GeoHash.withCharacterPrecision(
                        position.latitude, position.longitude, MODEL_LOCATION_GEOHASH_PRECISION
                    ).toBase32() else null,
                    user = user,
                    query = query
                )
            )
            launchLogVersion.update { it + 1 }
            requestHomeModelRefresh()
        }
    }

    fun toggleVisibility(application: Application, visibility: ApplicationVisibility) {
        coroutineScope.launch(Dispatchers.IO) {
            additionalPackageMetadataDao.upsert(
                UpdateVisibility(
                    packageName = application.packageName,
                    application.appInfo?.user.hashCode(),
                    hideFrom = when (visibility) {
                        ApplicationVisibility.VISIBLE -> null
                        ApplicationVisibility.HIDDEN_FROM_FILTERED -> HiddenPackageType.FILTERED
                        ApplicationVisibility.HIDDEN_FROM_TOP -> HiddenPackageType.TOP
                    }
                )
            )
            topAppsVisibilityVersion.update { it + 1 }
        }
    }

    fun getSimulatedTopApps(dayOfWeek: Int, minuteOfDay: Int, wifi: String?) = flow {
        emit(
            applicationLogEntryDao
                .simulatedTopApps(
                    dayOfWeek,
                    minuteOfDay,
                    wifi,
                    limit = SIMULATED_TOP_APPS_SQL_LIMIT
                )
                .asSequence()
                .mapNotNull(applicationService::toApplication)
                .toList()
        )
    }.flowOn(Dispatchers.IO)

    fun getUniqueSSIDs() = flow {
        emit(applicationLogEntryDao.getUniqueSSIDs())
    }.flowOn(Dispatchers.IO)

    fun setAlias(application: Application, alias: String) {
        coroutineScope.launch(Dispatchers.IO) {
            additionalPackageMetadataDao.upsert(
                UpdateAlias(
                    packageName = application.packageName,
                    application.appInfo?.user.hashCode(),
                    alias = alias
                )
            )
        }
    }

    fun exportDatabase(context: Context) {
        coroutineScope.launch(Dispatchers.IO) {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "application/octet-stream"

            database.query("pragma wal_checkpoint(full)", arrayOf()).use { cursor ->
                cursor.moveToFirst()
            }

            val uri = databaseUriProvider(context)

            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.flags = FLAG_GRANT_READ_URI_PERMISSION

            // The checkpoint and the FileProvider URI stay exactly as they are; only the launch
            // changes. It runs on a background dispatcher with a context that may be an
            // application context or an Activity that a configuration change has already
            // detached, so NEW_TASK is required and the main dispatcher keeps it off the IO
            // thread.
            val chooser = Intent.createChooser(intent, "Backup via:")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            withContext(Dispatchers.Main) { context.startActivity(chooser) }
        }
    }
}

/** Ranking input is plain coordinates; only the launch-log path keeps the raw [Location]. */
private fun Location?.asCoordinates(): Coordinates? =
    this?.let { Coordinates(latitude = it.latitude, longitude = it.longitude) }
