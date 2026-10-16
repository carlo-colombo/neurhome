package ovh.litapp.neurhome3.data.repositories

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.litapp.neurhome3.data.dao.SettingDao
import ovh.litapp.neurhome3.data.models.Setting

class SettingsRepositoryTest {
    private class FakeSettingDao : SettingDao {
        private val stored = MutableStateFlow<Map<String, String>>(emptyMap())

        override fun getAll(): List<Setting> = stored.value.map { Setting(it.key, it.value) }

        override fun insertOverride(setting: Setting) {
            stored.value = stored.value + (setting.key to setting.value)
        }

        override fun upsert(setting: Setting) {
            stored.value = stored.value + (setting.key to setting.value)
        }

        override fun like(c: String): Flow<List<Setting>> = stored.map { values ->
            values.filter { it.key.contains(c.trim('%')) }.map { Setting(it.key, it.value) }
        }

        override fun delete(setting: Setting) {
            stored.value = stored.value - setting.key
        }

        override fun get(s: String): Flow<List<Setting>> = stored.map { values ->
            values[s]?.let { listOf(Setting(s, it)) } ?: emptyList()
        }

        override fun getOnce(s: String): Setting? = stored.value[s]?.let { Setting(s, it) }
    }

    private fun repository(dao: SettingDao) =
        SettingsRepository(dao, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun classicIsTheDefaultForCleanInstalls() = runBlocking {
        val repository = repository(FakeSettingDao())

        assertEquals(HomeAppSelection.CLASSIC, repository.homeAppSelection.first())
    }

    @Test
    fun classicIsTheDefaultForExistingInstallsWithoutTheSetting() = runBlocking {
        val dao = FakeSettingDao()
        dao.upsert(Setting("show.calendar", "true"))
        dao.upsert(Setting("log.position", "true"))
        val repository = repository(dao)

        assertEquals(HomeAppSelection.CLASSIC, repository.homeAppSelection.first())
    }

    @Test
    fun selectionSurvivesProcessRestarts() = runBlocking {
        val dao = FakeSettingDao()
        repository(dao).setHomeAppSelection(HomeAppSelection.LEARNED)

        // A fresh repository over the same persisted table mimics a process restart.
        val restarted = repository(dao)

        assertEquals(HomeAppSelection.LEARNED, restarted.homeAppSelection.first())
        assertEquals("LEARNED", dao.getAll().single { it.key == HOME_APP_SELECTION_SETTING }.value)
    }

    @Test
    fun selectionCanBeSwitchedBackToClassic() = runBlocking {
        val dao = FakeSettingDao()
        val repository = repository(dao)
        repository.setHomeAppSelection(HomeAppSelection.LEARNED)
        repository.setHomeAppSelection(HomeAppSelection.CLASSIC)

        assertEquals(HomeAppSelection.CLASSIC, repository.homeAppSelection.first())
    }

    @Test
    fun unknownStoredValuesFallBackToClassic() {
        assertEquals(HomeAppSelection.CLASSIC, HomeAppSelection.fromStoredValue(""))
        assertEquals(HomeAppSelection.CLASSIC, HomeAppSelection.fromStoredValue("learned"))
        assertEquals(HomeAppSelection.CLASSIC, HomeAppSelection.fromStoredValue("SOMETHING_ELSE"))
        assertEquals(HomeAppSelection.LEARNED, HomeAppSelection.fromStoredValue("LEARNED"))
    }

    @Test
    fun choosingLearnedDoesNotEnableLoggingOptIns() = runBlocking {
        val dao = FakeSettingDao()
        val repository = repository(dao)

        repository.setHomeAppSelection(HomeAppSelection.LEARNED)

        assertEquals(false, repository.wifiLogging.first())
        assertEquals(false, repository.positionLogging.first())
    }

    @Test
    fun togglingAnUngatedSettingStoresTrueThenRemovesIt() = runBlocking {
        val dao = FakeSettingDao()
        val repository = repository(dao)

        repository.toggleShowAlternativeTime()
        assertEquals(true, repository.showAlternativeTime.first())

        repository.toggleShowAlternativeTime()
        assertEquals(false, repository.showAlternativeTime.first())
    }

    @Test
    fun enablingAGrantGatedSettingWithoutTheGrantIsNotPersisted() = runBlocking {
        val dao = FakeSettingDao()
        val repository = repository(dao)

        repository.setShowCalendar(enabled = true, permissionGranted = false)

        assertEquals(false, repository.showCalendar.first())
        assertTrue(dao.getAll().none { it.key == "show.calendar" })
    }

    @Test
    fun enablingAGrantGatedSettingWithTheGrantIsPersisted() = runBlocking {
        val dao = FakeSettingDao()
        val repository = repository(dao)

        repository.setShowCalendar(enabled = true, permissionGranted = true)

        assertEquals(true, repository.showCalendar.first())
        assertEquals("true", dao.getAll().single { it.key == "show.calendar" }.value)
    }

    @Test
    fun disablingAGrantGatedSettingIsPersistedEvenWithoutTheGrant() = runBlocking {
        val dao = FakeSettingDao()
        dao.upsert(Setting("show.calendar", "true"))
        val repository = repository(dao)

        repository.setShowCalendar(enabled = false, permissionGranted = false)

        assertEquals(false, repository.showCalendar.first())
        assertEquals("false", dao.getAll().single { it.key == "show.calendar" }.value)
    }

    @Test
    fun settingTheSameValueTwiceStaysIdempotent() = runBlocking {
        val dao = FakeSettingDao()
        val repository = repository(dao)

        repository.setShowCalendar(enabled = true, permissionGranted = true)
        repository.setShowCalendar(enabled = true, permissionGranted = true)
        assertEquals(true, repository.showCalendar.first())

        repository.setShowCalendar(enabled = false, permissionGranted = true)
        repository.setShowCalendar(enabled = false, permissionGranted = true)
        assertEquals(false, repository.showCalendar.first())
    }

    @Test
    fun alternativeTimeZoneIsEmptyWhenNothingWasStored() = runBlocking {
        assertEquals("", repository(FakeSettingDao()).alternativeTimeZone.first())
    }

    @Test
    fun alternativeTimeZoneRoundTripsThroughTheDaoUnderItsOwnKey() = runBlocking {
        val dao = FakeSettingDao()
        val repository = repository(dao)

        repository.setAlternativeTimeZone("Europe/Paris")
        assertEquals("Europe/Paris", repository.alternativeTimeZone.first())

        repository.setAlternativeTimeZone("America/New_York")
        assertEquals("America/New_York", repository.alternativeTimeZone.first())
        assertEquals(
            "America/New_York",
            dao.getAll().single { it.key == "alternative.time.zone" }.value,
        )
    }

    @Test
    fun eachGrantGatedSettingWritesItsOwnKey() = runBlocking {
        val dao = FakeSettingDao()
        val repository = repository(dao)

        repository.setWifiLogging(enabled = true, permissionGranted = true)
        repository.setPositionLogging(enabled = true, permissionGranted = true)
        repository.setShowCalendar(enabled = true, permissionGranted = true)
        repository.setShowStarredContacts(enabled = true, permissionGranted = true)

        assertEquals(true, repository.wifiLogging.first())
        assertEquals(true, repository.positionLogging.first())
        assertEquals(true, repository.showCalendar.first())
        assertEquals(true, repository.showStarredContacts.first())
    }
}
