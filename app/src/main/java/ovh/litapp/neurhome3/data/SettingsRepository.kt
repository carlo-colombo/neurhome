package ovh.litapp.neurhome3.data

import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SettingsRepository(
    val settingDao: SettingDao,
    val enableSSIDLogging: suspend () -> Unit,
    val disableSSIDLogging: () -> Unit
) {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    fun toggleWifiLogging() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                settingDao.insert(Setting("log.wifi", "true"))
                enableSSIDLogging()
            } catch (e: SQLiteConstraintException) {
                try {
                    settingDao.delete(Setting("log.wifi", "true"))
                    disableSSIDLogging()
                } catch (e: Exception) {
                    throw e
                }
            }
        }
    }

    val wifiLoggingSetting: Flow<Boolean> = settingDao.get("log.wifi").map {
        if (it.isEmpty()) false else it.first().value.toBoolean()
    }
}