package ovh.litapp.neurhome3.data.repositories

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ovh.litapp.neurhome3.ApplicationService
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.Setting
import ovh.litapp.neurhome3.data.dao.SettingDao

class FavouritesRepository(
    private val settingDao: SettingDao, private val applicationService: ApplicationService
) {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    val favouriteApps = settingDao.like("favourites.%").map { favourites ->
        val favouritesMap = favourites.associate { it.key to it.value }
        arrayOf(1, 2, 3, 4).mapNotNull { i ->
            favouritesMap["favourites.$i"]?.let { packageName ->
                applicationService.toApplication(packageName)?.let {
                    i to it
                }
            }
        }.toMap()
    }.flowOn(Dispatchers.IO)

    fun setFavourite(application: Application, index: Int) {
        coroutineScope.launch(Dispatchers.IO) {
            settingDao.insertOverride(Setting(key = "favourites.$index", application.packageName))
        }
    }
}