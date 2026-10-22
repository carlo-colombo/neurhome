package ovh.litapp.neurhome3.data.repositories

import android.Manifest
import android.content.Context
import android.content.pm.LauncherApps
import android.location.Location
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ovh.litapp.neurhome3.ApplicationService
import ovh.litapp.neurhome3.application.NeurhomeApplication
import ovh.litapp.neurhome3.data.AppDatabase
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.ApplicationVisibility
import ovh.litapp.neurhome3.data.dao.AdditionalPackageMetadataDao
import ovh.litapp.neurhome3.data.dao.ApplicationLogEntryDao
import ovh.litapp.neurhome3.data.dao.ContactsDAO
import ovh.litapp.neurhome3.data.models.ApplicationTag
import ovh.litapp.neurhome3.data.models.WifiContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

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

/**
 * The read model over the app data and the facade the ViewModels inject: it merges launcher apps,
 * the selected contacts and tag assignments into the lists the UI observes
 * ([applicationAndContacts], [applicationsForTag]) and forwards the writes and the per-concern
 * reads to the collaborator that owns them, so no screen has to know which one that is.
 *
 * The collaborators share [coroutineScope] because a repository is not reusable after the database
 * file is replaced; [close] stops all of them at once.
 */
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
    private val databaseExporter = DatabaseExporter(database, coroutineScope, databaseUriProvider)
    private val applicationServiceResolver = ApplicationServiceHomeAppResolver(applicationService)
    private val launchLog: LaunchLogRepository = LaunchLogRepository(
        applicationLogEntryDao = applicationLogEntryDao,
        scope = coroutineScope,
        onLaunchLogged = { homeRankingCoordinator.requestHomeModelRefresh() },
    )
    private val launcherAppsSource = LauncherAppsSource(
        applicationService = applicationService,
        launcherApps = launcherApps,
        additionalPackageMetadataDao = additionalPackageMetadataDao,
        scope = coroutineScope,
    )
    private val applicationMetadata = ApplicationMetadataRepository(
        additionalPackageMetadataDao = additionalPackageMetadataDao,
        tagRepository = tagRepository,
        scope = coroutineScope,
    )

    private val ticker = flow {
        while (true) {
            emit(42)
            delay(Duration.ofMinutes(5).toMillis())
        }
    }

    /**
     * The callable contact rows, as the selected [ContactCandidates] scope defines them. They are
     * shared rather than cold because every read keyed on a launch-log string resolves it against
     * this list: the merged list, the home ranking, the statistics simulation and the favourites
     * slots (which store a row identity). Read on IO, and kept after the last subscriber leaves so a
     * returning page never resolves a contact against an empty list.
     *
     * The provider has no invalidation signal, so the list is re-read on the same five minute tick
     * for every scope: a scope change re-reads immediately, and a whole-address-book read is only a
     * cursor scan, because contact photos are decoded lazily by the UI rather than per row.
     */
    val contacts: StateFlow<List<Application>> =
        combine(ticker, application.settingsRepository.contactCandidates) { _, candidates ->
            when {
                candidates == ContactCandidates.NONE -> listOf()
                !application.checkPermission(Manifest.permission.READ_CONTACTS) -> listOf()
                else -> contactsDAO.getContacts(
                    includeUnstarred = candidates == ContactCandidates.ALL
                )
            }
        }
            // An unchanged list must not re-merge the whole application list.
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)
            .stateIn(
                coroutineScope,
                SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = Long.MAX_VALUE),
                emptyList(),
            )

    /**
     * The two reads behind the home ranking (launch scores and launch counts) both scan the
     * four-month launch window, so they are cached once and shared with [packageFrequency].
     */
    private val rankingSource = CachedHomeAppRankingSource(
        delegate = homeAppRankingSource,
        launchLogVersion = { launchLog.launchLogVersion.value },
        topAppsVisibilityVersion = { applicationMetadata.topAppsVisibilityVersion.value },
    )

    private val homeRankingCoordinator = HomeRankingCoordinator(
        scope = coroutineScope,
        selection = application.settingsRepository.homeAppSelection,
        rankingSource = rankingSource,
        applicationService = applicationService,
        applicationLogEntryDao = applicationLogEntryDao,
        additionalPackageMetadataDao = additionalPackageMetadataDao,
        allApps = launcherAppsSource.apps,
        contacts = contacts,
        launchLogVersion = launchLog.launchLogVersion,
    )

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
        combine(launcherAppsSource.apps, tagAssignments) { apps, assignments ->
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
        launchLog.launchLogVersion.map { Unit },
        localDays.map { Unit },
    ).map {
        rankingSource.mostUsedApps().associate { it.packageName to it.score }
    }.distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    val applicationAndContacts: Flow<List<Application>> =
        combine(
            packageFrequency,
            contacts,
            launcherAppsSource.apps,
            tagAssignments
        ) { packageFrequency, contacts, allApps, assignments ->
            val tagsByApplication = tagNamesByApplication(assignments)
            (contacts.map { a ->
                // A contact row's identity is the key its launches are logged under, so its launch
                // count is read like an application's.
                a.copy(score = packageFrequency[a.packageName] ?: 0.0)
            } + allApps.map { a ->
                a.copy(
                    score = packageFrequency[a.packageName] ?: 0.0,
                ).withTags(tagsByApplication)
            }).sortedByLabelCaseInsensitive()
        }.distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    init {
        // The ranking's resolved-row cache is keyed on launcher rows, so it must drop an entry when
        // the launcher row behind it changes; see [CachedHomeAppResolver].
        launcherAppsSource.addPackageInvalidationListener(homeRankingCoordinator::invalidateResolvedRow)
    }

    /**
     * Stops all work started against the current database. A repository is not reusable
     * after the database file is replaced (`NeurhomeApplication.rebindDataLayer` builds a
     * new one), and the eager flows above would otherwise keep the closed database alive.
     */
    fun close() {
        coroutineScope.cancel()
    }

    fun applicationsForTag(tagName: String?): Flow<List<Application>> = applicationsWithTags.map { apps ->
        filterApplicationsForTag(apps, tagName)
    }

    fun setTags(application: Application, tags: Set<String>) =
        applicationMetadata.setTags(application, tags)

    /** Classic ranking, or a safe learned reranking of the same candidates. */
    fun getTopApps(
        n: Int,
        selection: Flow<HomeAppSelection>,
        getWifiContext: () -> WifiContext,
        getPosition: () -> Location?
    ): Flow<List<Application>> =
        homeRankingCoordinator.getTopApps(n, selection, getWifiContext, getPosition)

    fun logLaunch(
        packageName: String,
        user: Int,
        wifiContext: WifiContext,
        position: Location?,
        query: String? = null
    ) = launchLog.logLaunch(packageName, user, wifiContext, position, query)

    fun toggleVisibility(application: Application, visibility: ApplicationVisibility) =
        applicationMetadata.toggleVisibility(application, visibility)

    /**
     * The simulated statistics ranking. Its keys resolve exactly like the home ranking's, so a
     * starred contact launched often is listed with its own label, icon and call intent instead of
     * being dropped by the package lookup, which cannot turn a call URI back into a row.
     */
    fun getSimulatedTopApps(
        dayOfWeek: Int,
        minuteOfDay: Int,
        wifi: String?,
    ): Flow<List<Application>> =
        combine(launchLog.simulatedTopApps(dayOfWeek, minuteOfDay, wifi), contacts) { candidates, contactRows ->
            candidates.mapNotNull { resolveLoggedKey(it, contactRows, applicationServiceResolver) }
        }.flowOn(Dispatchers.IO)

    fun getUniqueSSIDs() = launchLog.getUniqueSSIDs()

    fun setAlias(application: Application, alias: String) =
        applicationMetadata.setAlias(application, alias)

    /** Kept on the repository so the existing settings screen keeps one dependency. */
    fun exportDatabase(context: Context) = databaseExporter.exportDatabase(context)
}
