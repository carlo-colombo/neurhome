package ovh.litapp.neurhome3.data.repositories

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import ovh.litapp.neurhome3.ApplicationService
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.models.Setting
import ovh.litapp.neurhome3.data.dao.SettingDao

class FavouritesRepository(
    private val settingDao: SettingDao,
    private val applicationService: ApplicationService,
    /**
     * The starred-contact rows a favourite can point at. A contact is stored under its own identity
     * — the call URI its launches are logged under — and only this list can turn that key back into
     * a row: the package lookup resolves packages, so it returns nothing for a call URI.
     */
    private val contacts: Flow<List<Application>>,
) {
    // SupervisorJob so one failed write does not cancel every later write.
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val applicationResolver = ApplicationServiceHomeAppResolver(applicationService)

    /**
     * The slots 1..4, keyed by slot number. A stored key resolves identity-first, exactly like a
     * home-ranking candidate: a key a loaded contact row carries is that row, call intent included,
     * and only a key no contact carries reaches the package lookup — which leaves every app
     * favourite exactly where it was. A slot that resolves to nothing is dropped, so the bottom bar
     * keeps showing its "no favourite" placeholder for that slot and the other slots keep their
     * numbers.
     */
    val favouriteApps: Flow<Map<Int, Application>> = combine(
        settingDao.like("favourites.%"),
        contacts,
    ) { favourites, contactRows ->
        val favouritesMap = favourites.associate { it.key to it.value }
        arrayOf(1, 2, 3, 4).mapNotNull { i ->
            favouritesMap["favourites.$i"]?.let { key ->
                // Profile 0 and no score: the values the package lookup was asked with before.
                val candidate = HomeAppCandidate(packageName = key, user = 0, score = 0.0)
                resolveLoggedKey(candidate, contactRows, applicationResolver)?.let { i to it }
            }
        }.toMap()
    }.flowOn(Dispatchers.IO)

    fun setFavourite(application: Application, index: Int) {
        coroutineScope.launch(Dispatchers.IO) {
            settingDao.insertOverride(Setting(key = "favourites.$index", application.packageName))
        }
    }
}
