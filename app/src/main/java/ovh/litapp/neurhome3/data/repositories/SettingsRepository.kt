package ovh.litapp.neurhome3.data.repositories

import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ovh.litapp.neurhome3.data.dao.SettingDao
import ovh.litapp.neurhome3.data.models.Setting

/** Home ranking strategy chosen by the user. Unknown values fall back to [CLASSIC]. */
enum class HomeAppSelection {
    CLASSIC,
    LEARNED;

    companion object {
        fun fromStoredValue(value: String): HomeAppSelection =
            entries.firstOrNull { it.name == value } ?: CLASSIC
    }
}

const val HOME_APP_SELECTION_SETTING = "home.app.selection"

class SettingsRepository(
    val settingDao: SettingDao,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
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

    /**
     * The user's Home ranking choice. It is stored independently of the logging
     * opt-ins and of runtime prerequisites, so a later revocation keeps the
     * preference instead of silently resetting it.
     */
    val homeAppSelection: Flow<HomeAppSelection> = get(HOME_APP_SELECTION_SETTING).map {
        HomeAppSelection.fromStoredValue(it)
    }

    fun setHomeAppSelection(selection: HomeAppSelection) =
        set(HOME_APP_SELECTION_SETTING, selection.name)

    private fun toggleSetting(
        key: String
    ): () -> Job = {
        coroutineScope.launch {
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

    private fun set(s: String, value: String) = coroutineScope.launch {
       settingDao.upsert(Setting(s, value))
    }
    private fun get(s: String) = settingDao.get(s).map {
        if (it.isEmpty()) "" else it.first().value
    }
}
