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
