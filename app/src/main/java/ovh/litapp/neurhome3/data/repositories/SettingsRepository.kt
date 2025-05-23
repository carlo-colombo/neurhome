package ovh.litapp.neurhome3.data.repositories

import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ovh.litapp.neurhome3.data.dao.SettingDao
import ovh.litapp.neurhome3.data.models.Setting

class SettingsRepository(
    val settingDao: SettingDao
) {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    val wifiLogging: Flow<Boolean> = getSetting("log.wifi")
    val toggleWifiLogging = toggleSetting("log.wifi")

    val positionLogging: Flow<Boolean> = getSetting("log.position")
    val togglePositionLogging = toggleSetting("log.position")

    val showCalendar: Flow<Boolean> = getSetting("show.calendar")
    val toggleShowCalendar = toggleSetting("show.calendar")

    val showStarredContacts: Flow<Boolean> = getSetting("show.contacts.starred")
    val toggleShowStarredContacts = toggleSetting("show.contacts.starred")

    val showAlternativeTime: Flow<Boolean> = getSetting("show.alternative.time")
    val toggleShowAlternativeTime = toggleSetting("show.alternative.time")

    val alternativeTimeZone: Flow<String> = get("alternative.time.zone")
    fun setAlternativeTimeZone(value: String) = set("alternative.time.zone", value)

    private fun toggleSetting(
        key: String
    ): () -> Job = {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                settingDao.insert(Setting(key, "true"))
            } catch (e: SQLiteConstraintException) {
                try {
                    settingDao.delete(Setting(key, "true"))
                } catch (e: Exception) {
                    throw e
                }
            }
        }
    }

    private fun getSetting(s: String) = settingDao.get(s).map {
        if (it.isEmpty()) false else it.first().value.toBoolean()
    }

    private fun set(s: String, value: String) = coroutineScope.launch(Dispatchers.IO) {
       settingDao.upsert(Setting(s, value))
    }
    private fun get(s: String) = settingDao.get(s).map {
        if (it.isEmpty()) "" else it.first().value.toString()
    }
}