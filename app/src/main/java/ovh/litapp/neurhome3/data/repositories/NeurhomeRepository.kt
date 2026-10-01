package ovh.litapp.neurhome3.data.repositories

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import ch.hsr.geohash.GeoHash
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import ovh.litapp.neurhome3.ApplicationService
import ovh.litapp.neurhome3.application.NeurhomeApplication
import ovh.litapp.neurhome3.data.AppDatabase
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.ApplicationVisibility
import ovh.litapp.neurhome3.data.NeurhomeFileProvider
import ovh.litapp.neurhome3.data.dao.AdditionalPackageMetadataDao
import ovh.litapp.neurhome3.data.dao.ApplicationLogEntryDao
import ovh.litapp.neurhome3.data.dao.ContactsDAO
import ovh.litapp.neurhome3.data.dao.UpdateAlias
import ovh.litapp.neurhome3.data.dao.UpdateVisibility
import ovh.litapp.neurhome3.data.models.ApplicationLogEntry
import ovh.litapp.neurhome3.data.models.ApplicationTag
import ovh.litapp.neurhome3.data.models.HiddenPackageType
import ovh.litapp.neurhome3.data.models.WifiContext
import ovh.litapp.neurhome3.data.ml.HomeAppContextEncoder
import ovh.litapp.neurhome3.data.ml.HomeAppLabel
import ovh.litapp.neurhome3.data.ml.PairwiseHomeAppReranker
import ovh.litapp.neurhome3.data.ml.PairwiseHomeAppTrainingDataFactory
import ovh.litapp.neurhome3.data.ml.SupportedHomeAppCandidate
import ovh.litapp.neurhome3.data.models.MODEL_LOCATION_GEOHASH_PRECISION
import java.time.LocalDateTime
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val TAG = "NeurhomeRepository"
private const val LAUNCHER_ICON_CACHE_SIZE = 256

private fun tagNamesByApplication(assignments: List<ApplicationTag>): Map<Pair<String, Int>, List<String>> =
    assignments.groupBy { it.packageName to it.profile }
        .mapValues { (_, values) -> values.map { it.tagName }.sortedBy(String::lowercase) }

private fun Application.withTags(
    tagsByApplication: Map<Pair<String, Int>, List<String>>,
): Application = copy(
    tags = tagsByApplication[packageName to (appInfo?.user?.hashCode() ?: 0)].orEmpty()
)

internal fun filterApplicationsForTag(
    applications: List<Application>,
    tagName: String?,
): List<Application> = applications.filter { app ->
    if (tagName == null) app.tags.isEmpty() else tagName in app.tags
}.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

@OptIn(ExperimentalCoroutinesApi::class)
class NeurhomeRepository(
    private val applicationLogEntryDao: ApplicationLogEntryDao,
    private val additionalPackageMetadataDao: AdditionalPackageMetadataDao,
    private val applicationService: ApplicationService,
    private val contactsDAO: ContactsDAO,
    val application: NeurhomeApplication,
    val database: AppDatabase,
    val launcherApps: LauncherApps,
    val userManager: UserManager,
    private val tagRepository: TagRepository,
) {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val launcherIconCache = LauncherIconCache<Drawable>(LAUNCHER_ICON_CACHE_SIZE)
    private val homeReranker = PairwiseHomeAppReranker()
    private val homeModel = MutableStateFlow<PairwiseHomeAppReranker.Model?>(null)
    private var modelRefreshJob: kotlinx.coroutines.Job? = null

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

    private val listAllLauncherApps = callbackFlow {
        fun refresh() {
            trySend(launcherApps.profiles.flatMap { launcherApps.getActivityList(null, it) })
        }

        fun invalidate(packageName: String, user: UserHandle) {
            launcherIconCache.invalidatePackage(packageName, user.hashCode())
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
        additionalPackageMetadataDao.list()
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

    private val applicationsWithTags = combine(allApps, tagRepository.assignments) { apps, assignments ->
        val tagsByApplication = tagNamesByApplication(assignments)
        apps.map { app ->
            app.withTags(tagsByApplication)
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }.flowOn(Dispatchers.IO)

    private val packageFrequency = flow {
        while (true) {
            emit(
                applicationLogEntryDao
                    .mostUsedApps()
            )
            delay(Duration.ofMinutes(5).toMillis())
        }
    }.map { packageCounts ->
        packageCounts.associate { it.packageName to it.score }
    }.flowOn(Dispatchers.IO)

    val applicationAndContacts: Flow<List<Application>> =
        combine(
            packageFrequency,
            contacts,
            allApps,
            tagRepository.assignments
        ) { packageFrequency, contacts, allApps, assignments ->
            val tagsByApplication = tagNamesByApplication(assignments)
            (contacts.map { a ->
                a.copy(score = packageFrequency[a.intent?.data.toString()] ?: 0.0)
            } + allApps.map { a ->
                a.copy(
                    score = packageFrequency[a.packageName] ?: 0.0,
                ).withTags(tagsByApplication)
            }).sortedBy { it.label.lowercase() }
        }.flowOn(Dispatchers.IO)

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

    fun getTopApps(n: Int = 6) = flow {
        while (true) {
            val quietModes = applicationService.quietModes(userManager)
            emit(
                applicationLogEntryDao
                    .topAppsByScore()
                    .asSequence()
                    .filter {!quietModes.getOrDefault(it.user, false) }
                    .mapNotNull(applicationService::toApplication)
                    .take(n)
                    .toList()
            )
            delay(Duration.ofSeconds(30).toMillis())
        }
    }.flowOn(Dispatchers.IO)

    /** Classic ranking, or a safe learned reranking of the same candidates. */
    fun getTopApps(
        n: Int,
        selection: Flow<HomeAppSelection>,
        getWifiContext: () -> WifiContext,
        getPosition: () -> Location?
    ): Flow<List<Application>> = selection.flatMapLatest { choice ->
        homeModel.flatMapLatest { model ->
            flow {
                while (true) {
                    emit(getTopAppsOnce(n, choice, model, getWifiContext(), getPosition()))
                    delay(Duration.ofSeconds(30).toMillis())
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun getTopAppsOnce(
        n: Int,
        selection: HomeAppSelection,
        model: PairwiseHomeAppReranker.Model?,
        wifi: WifiContext,
        position: Location?
    ): List<Application> {
        val quietModes = applicationService.quietModes(userManager)
        val classic = applicationLogEntryDao.topAppsByScore()
            .filter { !quietModes.getOrDefault(it.user, false) }
        if (selection != HomeAppSelection.LEARNED || model == null || !model.isUsable || position == null) {
            return classic.asSequence().mapNotNull(applicationService::toApplication).take(n).toList()
        }

        val counts = applicationLogEntryDao.mostUsedApps().associate { (it.packageName to it.user) to it.score.toInt() }
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
        val ranked = homeReranker.rerank(candidates, context, model)
        val byLabel = classic.associateBy { HomeAppLabel(it.packageName, it.user) }
        return ranked.asSequence().mapNotNull { label ->
            byLabel[label]?.let(applicationService::toApplication)
        }.take(n).toList().ifEmpty {
            classic.asSequence().mapNotNull(applicationService::toApplication).take(n).toList()
        }
    }

    private fun refreshHomeModel() {
        modelRefreshJob?.cancel()
        modelRefreshJob = coroutineScope.launch(Dispatchers.IO) {
            val launches = applicationLogEntryDao.all()
            val hidden = additionalPackageMetadataDao.list().first()
                .filter { it.hideFrom == HiddenPackageType.TOP }
                .map { it.packageName }
                .toSet()
            val examples = PairwiseHomeAppTrainingDataFactory.build(launches, hidden)
            homeModel.value = homeReranker.train(examples)
        }
    }

    init {
        refreshHomeModel()
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
            refreshHomeModel()
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
        }
    }

    fun getSimulatedTopApps(dayOfWeek: Int, minuteOfDay: Int, wifi: String?) = flow {
        emit(
            applicationLogEntryDao
                .simulatedTopApps(dayOfWeek, minuteOfDay, wifi)
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

            val cursor = database.query("pragma wal_checkpoint(full)", arrayOf())

            cursor.moveToFirst()

            val uri = NeurhomeFileProvider().getDatabaseURI(context)

            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.flags = FLAG_GRANT_READ_URI_PERMISSION

            context.startActivity(Intent.createChooser(intent, "Backup via:"))
        }
    }
}
