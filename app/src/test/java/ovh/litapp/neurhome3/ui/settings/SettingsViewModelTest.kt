package ovh.litapp.neurhome3.ui.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import ovh.litapp.neurhome3.application.NeurhomeApplication
import ovh.litapp.neurhome3.data.dao.SettingDao
import ovh.litapp.neurhome3.data.models.Setting
import ovh.litapp.neurhome3.data.repositories.ContactCandidates
import ovh.litapp.neurhome3.data.repositories.HomeAppSelection
import ovh.litapp.neurhome3.data.repositories.SettingsRepository

/**
 * Covers [SettingsViewModel]'s state tower: seven independent settings flows are combined into one
 * [SettingsUiState] (five in the first `combine`, then the time zone and the ranking selection in
 * two further `combine`s), so a wrong nesting or a dropped field would silently show one setting
 * in another row - the kind of wiring bug that no pure helper test can see.
 *
 * The repository runs over a fake [SettingDao] and writes through `Dispatchers.Unconfined`, so
 * every write lands before the assertions, and the ViewModel runs on `runTest` virtual time.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {
    /**
     * `SettingsViewModel` needs a `NeurhomeRepository` for `exportDatabase`, but nothing in these
     * tests exports anything: the Robolectric application is only the way to obtain the concrete
     * (and seam-less) instance the constructor demands.
     */
    private val application =
        RuntimeEnvironment.getApplication() as NeurhomeApplication

    private val neurhomeRepository = application.repository

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun everySettingsFlowLandsInItsOwnUiStateField() = runTest(dispatcher) {
        val dao = FakeSettingDao()
        val repository = SettingsRepository(dao, CoroutineScope(Dispatchers.Unconfined))
        val viewModel = SettingsViewModel(repository, neurhomeRepository)
        val states = mutableListOf<SettingsUiState>()
        backgroundScope.launch { viewModel.uiState.collect { states += it } }
        runCurrent()

        assertEquals(listOf(SettingsUiState()), states)

        repository.setWifiLogging(enabled = true, permissionGranted = true)
        repository.setPositionLogging(enabled = true, permissionGranted = true)
        repository.setShowCalendar(enabled = true, permissionGranted = true)
        repository.setContactCandidates(ContactCandidates.STARRED, permissionGranted = true)
        repository.toggleShowAlternativeTime()
        repository.setAlternativeTimeZone("Europe/Paris")
        repository.setHomeAppSelection(HomeAppSelection.LEARNED)
        runCurrent()
        advanceUntilIdle()

        assertEquals(
            SettingsUiState(
                logWiFi = true,
                logPosition = true,
                showCalendar = true,
                contactCandidates = ContactCandidates.STARRED,
                showAlternativeTime = true,
                alternativeTimeZone = "Europe/Paris",
                homeAppSelection = HomeAppSelection.LEARNED,
            ),
            states.last(),
        )
    }

    @Test
    fun oneChangedFlowDoesNotDisturbTheOthers() = runTest(dispatcher) {
        val dao = FakeSettingDao()
        val repository = SettingsRepository(dao, CoroutineScope(Dispatchers.Unconfined))
        val viewModel = SettingsViewModel(repository, neurhomeRepository)
        val states = mutableListOf<SettingsUiState>()
        backgroundScope.launch { viewModel.uiState.collect { states += it } }
        runCurrent()

        repository.setWifiLogging(enabled = true, permissionGranted = true)
        repository.setAlternativeTimeZone("Europe/Paris")
        runCurrent()
        advanceUntilIdle()
        val both = states.last()
        assertEquals(true, both.logWiFi)
        assertEquals("Europe/Paris", both.alternativeTimeZone)

        repository.setWifiLogging(enabled = false, permissionGranted = false)
        runCurrent()
        advanceUntilIdle()

        assertEquals(
            SettingsUiState(alternativeTimeZone = "Europe/Paris"),
            states.last(),
        )
    }

    @Test
    fun theViewModelWritesReachTheRepository() = runTest(dispatcher) {
        val dao = FakeSettingDao()
        val repository = SettingsRepository(dao, CoroutineScope(Dispatchers.Unconfined))
        val viewModel = SettingsViewModel(repository, neurhomeRepository)

        viewModel.setWifiLogging(enabled = true, permissionGranted = true)
        viewModel.setPositionLogging(enabled = true, permissionGranted = false)
        viewModel.setShowCalendar(enabled = true, permissionGranted = true)
        viewModel.setContactCandidates(ContactCandidates.ALL, permissionGranted = true)
        viewModel.toggleShowAlternativeTime()
        viewModel.saveTimeZone("Europe/Paris")
        viewModel.setHomeAppSelection(HomeAppSelection.LEARNED)
        advanceUntilIdle()

        // A grant-gated opt-in without the grant is dropped by the repository instead of stored.
        assertEquals(
            mapOf(
                "log.wifi" to "true",
                "show.calendar" to "true",
                "contacts.call.candidates" to "ALL",
                "show.alternative.time" to "true",
                "alternative.time.zone" to "Europe/Paris",
                "home.app.selection" to "LEARNED",
            ),
            dao.storedKeys(),
        )
    }

    /**
     * Only the reads and writes [SettingsRepository] performs are meaningful here; `insertOverride`
     * and `like` belong to `FavouritesRepository` and fail loudly rather than quietly behaving like
     * another query. The same fixture as `SettingsRepositoryTest`.
     */
    private class FakeSettingDao : SettingDao {
        private val stored = MutableStateFlow<Map<String, String>>(emptyMap())

        override fun getAll(): List<Setting> = stored.value.map { Setting(it.key, it.value) }

        override fun insertOverride(setting: Setting): Unit = unsupported("insertOverride")

        override fun upsert(setting: Setting) {
            stored.value = stored.value + (setting.key to setting.value)
        }

        override fun like(c: String): Flow<List<Setting>> = unsupported("like")

        override fun delete(setting: Setting) {
            stored.value = stored.value - setting.key
        }

        override fun get(s: String): Flow<List<Setting>> = stored.map { values ->
            values[s]?.let { listOf(Setting(s, it)) } ?: emptyList()
        }

        override fun getOnce(s: String): Setting? = stored.value[s]?.let { Setting(s, it) }

        fun storedKeys(): Map<String, String> = stored.value

        private fun unsupported(query: String): Nothing =
            throw UnsupportedOperationException("$query is not part of SettingsRepository")
    }
}
