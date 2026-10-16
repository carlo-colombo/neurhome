package ovh.litapp.neurhome3.application

import android.content.Context
import android.os.UserManager
import androidx.room.withTransaction
import ovh.litapp.neurhome3.ApplicationService
import ovh.litapp.neurhome3.data.AppDatabase
import ovh.litapp.neurhome3.data.NeurhomeFileProvider
import ovh.litapp.neurhome3.data.dao.CalendarDAO
import ovh.litapp.neurhome3.data.dao.ContactsDAO
import ovh.litapp.neurhome3.data.repositories.CalendarRepository
import ovh.litapp.neurhome3.data.repositories.FavouritesRepository
import ovh.litapp.neurhome3.data.repositories.NeurhomeRepository
import ovh.litapp.neurhome3.data.repositories.RoomHomeAppRankingSource
import ovh.litapp.neurhome3.data.repositories.SettingsRepository
import ovh.litapp.neurhome3.data.repositories.TagRepository

/**
 * Everything `NeurhomeApplication` binds to the Room database file: the database itself, the
 * repositories holding its DAOs, and the [ApplicationService] they resolve launcher activities
 * through.
 *
 * Room keeps one database per process and never reopens a closed instance, so none of these can be
 * cached for the whole process lifetime: [NeurhomeApplication.replaceDatabase] calls [close] and
 * then [rebind] around the file swap. The delegate fields are volatile because the relaunched UI
 * and the settings collectors read them off the main thread.
 */
internal class DataLayerGraph(private val application: NeurhomeApplication) {

    private val applicationService by lazy {
        ApplicationService(
            packageManager = application.packageManager,
            launcherApps = application.launcherApps,
            userManager = application.getSystemService(Context.USER_SERVICE) as UserManager,
        )
    }

    @Volatile
    private var databaseDelegate = lazy { AppDatabase.getDatabase(application) }
    val database: AppDatabase
        get() = databaseDelegate.value

    @Volatile
    private var repositoryDelegate = lazy { createRepository() }
    val repository: NeurhomeRepository
        get() = repositoryDelegate.value

    @Volatile
    private var favouritesRepositoryDelegate = lazy { createFavouritesRepository() }
    val favouritesRepository: FavouritesRepository
        get() = favouritesRepositoryDelegate.value

    @Volatile
    private var settingsRepositoryDelegate = lazy { createSettingsRepository() }
    val settingsRepository: SettingsRepository
        get() = settingsRepositoryDelegate.value

    @Volatile
    private var tagRepositoryDelegate = lazy { createTagRepository() }
    val tagRepository: TagRepository
        get() = tagRepositoryDelegate.value

    @Volatile
    private var calendarRepositoryDelegate = lazy { createCalendarRepository() }
    val calendarRepository: CalendarRepository
        get() = calendarRepositoryDelegate.value

    private fun createRepository() = NeurhomeRepository(
        applicationLogEntryDao = database.applicationLogEntryDao(),
        additionalPackageMetadataDao = database.hiddenPackageDao(),
        contactsDAO = ContactsDAO(application),
        application = application,
        database = database,
        launcherApps = application.launcherApps,
        applicationService = applicationService,
        homeAppRankingSource = RoomHomeAppRankingSource(
            applicationLogEntryDao = database.applicationLogEntryDao(),
            quietModes = applicationService::quietModes,
        ),
        tagRepository = tagRepository,
        databaseUriProvider = NeurhomeFileProvider()::getDatabaseURI
    )

    private fun createFavouritesRepository() = FavouritesRepository(
        settingDao = database.settingDao(),
        applicationService = applicationService
    )

    private fun createSettingsRepository() = SettingsRepository(database.settingDao())

    private fun createTagRepository() = TagRepository(
        database.tagDao(), database.applicationTagDao()
    ) { block ->
        database.withTransaction { block() }
    }

    private fun createCalendarRepository() = CalendarRepository(
        application::checkPermission, settingsRepository, CalendarDAO(application)
    )

    /**
     * Closes the database and the repository holding its DAOs so the database file can be
     * replaced. The repository's eager flows are stopped first (BUG-05); a closed database cannot
     * serve them.
     */
    fun close() {
        closeRepository()
        database.close()
    }

    /**
     * Rebuilds every singleton bound to the database file that was just replaced. The closed
     * instances cannot serve the new file, and the relaunch that follows in `MainActivity` builds
     * fresh ViewModels on top of these.
     */
    @Synchronized
    fun rebind() {
        closeRepository()
        AppDatabase.forgetInstance()
        databaseDelegate = lazy { AppDatabase.getDatabase(application) }
        settingsRepositoryDelegate = lazy { createSettingsRepository() }
        favouritesRepositoryDelegate = lazy { createFavouritesRepository() }
        tagRepositoryDelegate = lazy { createTagRepository() }
        calendarRepositoryDelegate = lazy { createCalendarRepository() }
        repositoryDelegate = lazy { createRepository() }
    }

    private fun closeRepository() {
        if (repositoryDelegate.isInitialized()) repository.close()
    }
}
