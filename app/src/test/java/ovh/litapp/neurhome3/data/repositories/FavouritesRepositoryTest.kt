package ovh.litapp.neurhome3.data.repositories

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.UserManager
import androidx.room.Room
import kotlinx.coroutines.flow.MutableStateFlow
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
import ovh.litapp.neurhome3.data.dao.ApplicationLogEntryDao.PackageCount
import ovh.litapp.neurhome3.data.models.Setting

/**
 * Covers the `favourites.<slot>` mapping behind [FavouritesRepository.favouriteApps] against the
 * real settings SQL: only slots 1..4 are read, each resolved slot keeps its slot number, and a slot
 * whose key resolves to nothing is dropped instead of shifting the others. The slot write behind
 * [FavouritesRepository.setFavourite] runs against the same real SQL, because it is the only caller
 * of `SettingDao.insertOverride` and its overwrite semantics are invisible to a fake that can also
 * implement `upsert` with the same body.
 *
 * A starred contact is a favourite like an application, stored under the identity its launches are
 * logged with (its call URI), so the tests below pin both halves of the resolution: a contact key
 * is matched against the loaded contact rows and must never reach the package lookup, and every
 * other key keeps the package lookup it had.
 * [ApplicationService] is replaced by [ShadowApplicationService] because the real package lookup
 * needs a launcher binder.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, shadows = [ShadowApplicationService::class])
class FavouritesRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: FavouritesRepository
    private val contacts = MutableStateFlow<List<HomeApplication>>(emptyList())

    @Before
    fun setUp() {
        ShadowApplicationService.reset()
        contacts.value = emptyList()
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FavouritesRepository(database.settingDao(), applicationService(), contacts)
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
    fun aContactFavouriteResolvesToItsContactRowInsteadOfThePackageLookup() = runBlocking {
        val contact = contact("Ada", "+3912345678")
        contacts.value = listOf(contact)
        storeSlot(2, contact.packageName)

        assertEquals(mapOf(2 to contact), repository.favouriteApps.first())
        assertEquals(
            "a call URI must never reach the package lookup",
            emptyList<PackageCount>(),
            ShadowApplicationService.lookups,
        )
    }

    @Test
    fun settingAContactAsAFavouriteStoresItsCallUriAndRendersTheContact() = runBlocking {
        val contact = contact("Ada", "+3912345678")
        contacts.value = listOf(contact)

        repository.setFavourite(contact, index = 4)

        awaitStoredSlot(slot = 4, packageName = contact.packageName)
        // The stored key is the row identity: the call URI, percent-encoded like every Uri string.
        assertEquals("tel:%2B3912345678", contact.packageName)
        assertEquals(mapOf(4 to contact), repository.favouriteApps.first())
    }

    @Test
    fun anAppFavouriteStillResolvesThroughThePackageLookupWhileContactsAreLoaded() = runBlocking {
        val app = app("com.example.one")
        ShadowApplicationService.resolved[app.packageName] = app
        val contact = contact("Ada", "+3912345678")
        contacts.value = listOf(contact)
        storeSlot(1, app.packageName)
        storeSlot(3, contact.packageName)

        assertEquals(mapOf(1 to app, 3 to contact), repository.favouriteApps.first())
        assertEquals(
            "only the app favourite may reach the package lookup",
            listOf(PackageCount(app.packageName, user = 0, score = 0.0)),
            ShadowApplicationService.lookups,
        )
    }

    @Test
    fun aContactFavouriteWhoseRowIsGoneIsDroppedLikeAnyOtherUnresolvableSlot() = runBlocking {
        val removed = contact("Ada", "+3912345678")
        storeSlot(2, removed.packageName)

        assertEquals(emptyMap<Int, HomeApplication>(), repository.favouriteApps.first())
        assertEquals(
            "a key no loaded contact carries still falls through to the package lookup",
            listOf(PackageCount(removed.packageName, user = 0, score = 0.0)),
            ShadowApplicationService.lookups,
        )
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

    /** The row `ContactsDAO` builds for a starred contact: identity is the call URI it is launched with. */
    private fun contact(name: String, number: String): HomeApplication {
        val callUri = Uri.fromParts("tel", number, null)
        return HomeApplication(
            label = name,
            packageName = callUri.toString(),
            icon = null,
            intent = Intent(Intent.ACTION_CALL, callUri),
        )
    }

    private fun applicationService(): ApplicationService {
        val context = RuntimeEnvironment.getApplication()
        return ApplicationService(
            packageManager = context.packageManager,
            launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps,
            userManager = context.getSystemService(Context.USER_SERVICE) as UserManager,
        )
    }
}
