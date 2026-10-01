package ovh.litapp.neurhome3.data.repositories

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

        override fun insert(setting: Setting) {
            check(stored.value.containsKey(setting.key)) { "duplicate key ${setting.key}" }
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
}
