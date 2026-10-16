package ovh.litapp.neurhome3.data.repositories

import ovh.litapp.neurhome3.ApplicationService
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.dao.ApplicationLogEntryDao

/**
 * Room-backed [HomeAppRankingSource]. [quietModes] is a provider rather than a snapshot because
 * the profile set and its quiet state can change while the process lives.
 */
internal class RoomHomeAppRankingSource(
    private val applicationLogEntryDao: ApplicationLogEntryDao,
    private val quietModes: () -> Map<Int, Boolean>,
) : HomeAppRankingSource {
    override fun topAppsByScore(limit: Int): List<HomeAppCandidate> =
        applicationLogEntryDao.topAppsByScore(limit).map(::toCandidate)

    override fun mostUsedApps(): List<HomeAppCandidate> =
        applicationLogEntryDao.mostUsedApps().map(::toCandidate)

    override fun quietProfileIds(): Set<Int> = quietModes().filterValues { it }.keys

    private fun toCandidate(packageCount: ApplicationLogEntryDao.PackageCount) = HomeAppCandidate(
        packageName = packageCount.packageName,
        user = packageCount.user,
        score = packageCount.score,
    )
}

/** Resolves ranked candidates through [ApplicationService]'s launcher/package lookups. */
internal class ApplicationServiceHomeAppResolver(
    private val applicationService: ApplicationService,
) : HomeAppResolver {
    override fun resolve(candidate: HomeAppCandidate): Application? =
        applicationService.toApplication(
            ApplicationLogEntryDao.PackageCount(
                packageName = candidate.packageName,
                user = candidate.user,
                score = candidate.score,
            )
        )
}

/**
 * Resolves one launch-log key against the loaded starred-contact rows and, only when no row carries
 * it, through [delegate]. A starred contact is launched through an `ACTION_CALL` intent, so the key
 * it is logged under is its call URI, and no package lookup can turn that back into a row: the
 * loaded contact rows are matched by identity instead. A key no loaded contact carries falls through
 * to [delegate], which keeps the launcher-row path exactly where it was.
 *
 * Every read keyed on a launch-log string resolves it here: the ranking candidates, the favourites
 * slots (which store the row identity) and the statistics simulation. One function because three
 * copies of the rule is how the reads would start disagreeing about which key is which row.
 */
internal fun resolveLoggedKey(
    candidate: HomeAppCandidate,
    knownContacts: List<Application>,
    delegate: HomeAppResolver,
): Application? =
    knownContacts.firstOrNull { it.packageName == candidate.packageName }
        ?.let { it.copy(score = candidate.score) }
        ?: delegate.resolve(candidate)

/**
 * [resolveLoggedKey] with the contact rows behind a provider, so every resolution sees the list as
 * it is now: the ranking resolves many candidates per refresh and a contact removed meanwhile must
 * stop resolving.
 */
internal class ContactRowHomeAppResolver(
    /** The starred-contact rows the UI currently shows, read afresh so a removed contact drops out. */
    private val knownContacts: () -> List<Application>,
    private val delegate: HomeAppResolver,
) : HomeAppResolver {
    override fun resolve(candidate: HomeAppCandidate): Application? =
        resolveLoggedKey(candidate, knownContacts(), delegate)
}
