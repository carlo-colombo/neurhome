package ovh.litapp.neurhome3.data.repositories

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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

/**
 * Which contacts are call candidates: nothing, the starred subset, or the whole address book.
 * One value rather than two flags, because "all" already contains "starred" and two booleans could
 * describe a state that has no meaning.
 */
enum class ContactCandidates {
    NONE,
    STARRED,
    ALL;

    companion object {
        fun fromStoredValue(value: String): ContactCandidates =
            entries.firstOrNull { it.name == value } ?: NONE
    }
}

/** Where the chosen [ContactCandidates] value is stored. */
const val CONTACT_CANDIDATES_SETTING = "contacts.call.candidates"

/**
 * The boolean this setting replaced. It is never written any more, but it is still read: an install
 * that had starred contacts enabled must keep them without the user touching Settings again.
 */
const val LEGACY_SHOW_STARRED_CONTACTS_SETTING = "show.contacts.starred"

/** ISO local date on which [LogRetention] last pruned the launch log. */
const val LOG_RETENTION_LAST_RUN_SETTING = "log.retention.last.run"

class SettingsRepository(
    val settingDao: SettingDao,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    val wifiLogging: Flow<Boolean> = getSetting("log.wifi")

    val positionLogging: Flow<Boolean> = getSetting("log.position")

    val showCalendar: Flow<Boolean> = getSetting("show.calendar")

    /**
     * The contact scope. An explicit stored value wins; otherwise the legacy starred flag decides,
     * so an upgrade reads back exactly what the user had chosen before.
     */
    val contactCandidates: Flow<ContactCandidates> = combine(
        get(CONTACT_CANDIDATES_SETTING),
        get(LEGACY_SHOW_STARRED_CONTACTS_SETTING),
    ) { stored, legacy ->
        when {
            stored.isNotBlank() -> ContactCandidates.fromStoredValue(stored)
            legacy.toBoolean() -> ContactCandidates.STARRED
            else -> ContactCandidates.NONE
        }
    }

    val showAlternativeTime: Flow<Boolean> = getSetting("show.alternative.time")
    val toggleShowAlternativeTime = toggleSetting("show.alternative.time")

    /**
     * Persists a permission-backed opt-in only while its grant exists. Turning it off is always
     * persisted, so a revoked grant cannot leave a stale "on" row; turning it on without the
     * grant is ignored, and the caller retries once the grant is observed. Idempotent: writing
     * the same value twice leaves the same row instead of flipping it.
     */
    fun setWifiLogging(enabled: Boolean, permissionGranted: Boolean) =
        setGrantGated("log.wifi", enabled, permissionGranted)

    fun setPositionLogging(enabled: Boolean, permissionGranted: Boolean) =
        setGrantGated("log.position", enabled, permissionGranted)

    fun setShowCalendar(enabled: Boolean, permissionGranted: Boolean) =
        setGrantGated("show.calendar", enabled, permissionGranted)

    /**
     * Same grant rule as the boolean opt-ins: turning contacts off is always persisted, choosing a
     * scope that reads contacts is ignored until the grant exists, and the selection is written as
     * the enum name so an unknown stored value can only ever fall back to [ContactCandidates.NONE].
     */
    fun setContactCandidates(candidates: ContactCandidates, permissionGranted: Boolean) =
        setGranted(
            key = CONTACT_CANDIDATES_SETTING,
            value = candidates.name,
            requiresGrant = candidates != ContactCandidates.NONE,
            permissionGranted = permissionGranted,
        )

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
            settingDao.toggle(key, "true")
        }
    }

    private fun setGrantGated(key: String, enabled: Boolean, permissionGranted: Boolean) =
        setGranted(key, enabled.toString(), requiresGrant = enabled, permissionGranted)

    private fun setGranted(
        key: String,
        value: String,
        requiresGrant: Boolean,
        permissionGranted: Boolean,
    ) {
        if (requiresGrant && !permissionGranted) return
        set(key, value)
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
