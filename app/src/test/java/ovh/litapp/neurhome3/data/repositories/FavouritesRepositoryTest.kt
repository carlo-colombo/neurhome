package ovh.litapp.neurhome3.data.repositories

import android.app.Application
import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserManager
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import ovh.litapp.neurhome3.ApplicationService
import ovh.litapp.neurhome3.ShadowApplicationService
import ovh.litapp.neurhome3.data.AppDatabase
import ovh.litapp.neurhome3.data.Application as HomeApplication
import ovh.litapp.neurhome3.data.models.Setting

/**
 * Covers the `favourites.<slot>` mapping behind [FavouritesRepository.favouriteApps] against the
 * real settings SQL: only slots 1..4 are read, each resolved application keeps its slot number, and
 * a slot whose package the launcher cannot resolve is dropped instead of shifting the others. The
 * slot write behind [FavouritesRepository.setFavourite] runs against the same real SQL, because it
 * is the only caller of `SettingDao.insertOverride` and its overwrite semantics are invisible to a
 * fake that can also implement `upsert` with the same body.
 * [ApplicationService] is replaced by [ShadowApplicationService] because the real package lookup
 * needs a launcher binder.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, shadows = [ShadowApplicationService::class])
class FavouritesRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: FavouritesRepository

    @Before
    fun setUp() {
        ShadowApplicationService.reset()
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FavouritesRepository(database.settingDao(), applicationService())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun everySlotMapsItsStoredPackageToItsSlotNumber() = runBlocking {
        val apps = (1..4).associateWith { slot ->
            app("com.example.slot$slot").also {
                ShadowApplicationService.resolved["com.example.slot$slot"] = it
            }
        }
        apps.forEach { (slot, app) -> storeSlot(slot, app.packageName) }

        assertEquals(apps, repository.favouriteApps.first())
    }

    @Test
    fun slotsOutsideOneToFourAreIgnored() = runBlocking {
        val app = app("com.example.one")
        ShadowApplicationService.resolved[app.packageName] = app
        storeSlot(1, app.packageName)
        storeSlot(0, app.packageName)
        storeSlot(5, app.packageName)

        assertEquals(mapOf(1 to app), repository.favouriteApps.first())
    }

    @Test
    fun aSlotWithoutAResolvableApplicationIsDroppedAndTheOthersKeepTheirSlots() = runBlocking {
        val app = app("com.example.one")
        ShadowApplicationService.resolved[app.packageName] = app
        storeSlot(1, app.packageName)
        storeSlot(3, "com.example.uninstalled")

        assertEquals(mapOf(1 to app), repository.favouriteApps.first())
    }

    @Test
    fun anEmptySettingsTableYieldsNoFavourites() = runBlocking {
        assertEquals(emptyMap<Int, HomeApplication>(), repository.favouriteApps.first())
    }

    @Test
    fun settingASlotOverwritesThePackageStoredThereBefore() = runBlocking {
        val previous = app("com.example.old")
        val replacement = app("com.example.new")
        ShadowApplicationService.resolved[previous.packageName] = previous
        ShadowApplicationService.resolved[replacement.packageName] = replacement
        storeSlot(3, previous.packageName)

        repository.setFavourite(replacement, index = 3)

        awaitStoredSlot(slot = 3, packageName = replacement.packageName)
        // The slot keeps one row for the slot number, now holding the new package.
        assertEquals(mapOf(3 to replacement), repository.favouriteApps.first())
    }

    /**
     * `setFavourite` writes from a coroutine on `Dispatchers.IO`, so the row lands on another thread
     * than the test's.
     */
    private fun awaitStoredSlot(slot: Int, packageName: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (database.settingDao().getOnce("favourites.$slot")?.value != packageName) {
            check(System.nanoTime() < deadline) { "favourites.$slot was not written within 5s" }
            Thread.sleep(5)
        }
    }

    private fun storeSlot(slot: Int, packageName: String) {
        database.settingDao().upsert(Setting(key = "favourites.$slot", value = packageName))
    }

    private fun app(packageName: String) =
        HomeApplication(label = packageName, packageName = packageName, icon = null)

    private fun applicationService(): ApplicationService {
        val context = RuntimeEnvironment.getApplication()
        return ApplicationService(
            packageManager = context.packageManager,
            launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps,
            userManager = context.getSystemService(Context.USER_SERVICE) as UserManager,
        )
    }
}
