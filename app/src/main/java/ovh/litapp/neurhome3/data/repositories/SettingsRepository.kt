package ovh.litapp.neurhome3.data.repositories

import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ovh.litapp.neurhome3.data.Setting
import ovh.litapp.neurhome3.data.dao.SettingDao

class SettingsRepository(
    val settingDao: SettingDao,
    enableSSIDLogging: suspend () -> Unit,
    disableSSIDLogging: () -> Unit
) {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    val wifiLogging: Flow<Boolean> = getSetting("log.wifi")
    val toggleWifiLogging = toggleSetting("log.wifi", enableSSIDLogging, disableSSIDLogging)

    val positionLogging: Flow<Boolean> = getSetting("log.position")
    val togglePositionLogging = toggleSetting("log.position")

    val showCalendar: Flow<Boolean> = getSetting("show.calendar")
    val toggleShowCalendar = toggleSetting("show.calendar")

    val showStarredContacts: Flow<Boolean> = getSetting("show.contacts.starred")
    val toggleShowStarredContacts = toggleSetting("show.contacts.starred")

    private fun toggleSetting(
        key: String,
        onEnable: suspend () -> Unit = {},
        onDisable: suspend () -> Unit = {}
    ): () -> Job = {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                settingDao.insert(Setting(key, "true"))
                onEnable()
            } catch (e: SQLiteConstraintException) {
                try {
                    settingDao.delete(Setting(key, "true"))
                    onDisable()
                } catch (e: Exception) {
                    throw e
                }
            }
        }
    }

    private fun getSetting(s: String) = settingDao.get(s).map {
        if (it.isEmpty()) false else it.first().value.toBoolean()
    }
}