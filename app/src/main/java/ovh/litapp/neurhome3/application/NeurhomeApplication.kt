package ovh.litapp.neurhome3.application

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.UserManager
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import ovh.litapp.neurhome3.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ovh.litapp.neurhome3.ApplicationService
import ovh.litapp.neurhome3.data.AppDatabase
import ovh.litapp.neurhome3.data.NEURHOME_DATABASE
import ovh.litapp.neurhome3.data.NeurhomeFileProvider
import ovh.litapp.neurhome3.data.dao.CalendarDAO
import ovh.litapp.neurhome3.data.dao.ContactsDAO
import ovh.litapp.neurhome3.data.repositories.CalendarRepository
import ovh.litapp.neurhome3.data.repositories.ClockAlarmRepository
import ovh.litapp.neurhome3.data.repositories.FavouritesRepository
import ovh.litapp.neurhome3.data.repositories.NeurhomeRepository
import ovh.litapp.neurhome3.data.repositories.RoomHomeAppRankingSource
import ovh.litapp.neurhome3.data.repositories.SettingsRepository
import ovh.litapp.neurhome3.data.repositories.TagRepository
import ovh.litapp.neurhome3.data.weather.WeatherRepository
import ovh.litapp.neurhome3.data.weather.WeatherServiceImpl
import ovh.litapp.neurhome3.data.models.WifiContext
import java.io.FileOutputStream


class NeurhomeApplication : Application() {
    // No need to cancel this scope as it'll be torn down with the process
    private val applicationScope = CoroutineScope(SupervisorJob())

    // Room's process-wide instance and every repository holding its DAOs are unusable once
    // the database file is replaced, so these are rebuilt by [rebindDataLayer] instead of
    // being cached for the whole process lifetime. The delegates are volatile because the
    // relaunched UI and the settings collectors below read them off the main thread.
    @Volatile
    private var databaseDelegate = lazy { AppDatabase.getDatabase(this) }
    private val database: AppDatabase
        get() = databaseDelegate.value

    private val applicationService by lazy {
        ApplicationService(
            packageManager = packageManager,
            launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
        )
    }

    /** Quiet-mode lookups for the home ranking; the profile set is re-read per lookup. */
    private val userManager by lazy { getSystemService(Context.USER_SERVICE) as UserManager }

    @Volatile
    private var repositoryDelegate = lazy { createRepository() }
    val repository: NeurhomeRepository
        get() = repositoryDelegate.value

    /** One CIO-backed weather service per process; the owned HttpClient is never closed
     *  otherwise, so creating one per HomeViewModel would leak thread pools. */
    val weatherRepository by lazy {
        WeatherRepository(WeatherServiceImpl())
    }

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

    val alarmRepository by lazy {
        ClockAlarmRepository(this)
    }

    private fun createRepository() = NeurhomeRepository(
        applicationLogEntryDao = database.applicationLogEntryDao(),
        additionalPackageMetadataDao = database.hiddenPackageDao(),
        contactsDAO = ContactsDAO(this),
        application = this,
        database = database,
        launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps,
        applicationService = applicationService,
        homeAppRankingSource = RoomHomeAppRankingSource(
            applicationLogEntryDao = database.applicationLogEntryDao(),
            quietModes = { applicationService.quietModes(userManager) },
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

    private fun createCalendarRepository() =
        CalendarRepository(::checkPermission, settingsRepository, CalendarDAO(this))

    fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            this, permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Wi-Fi and location launch-logging state; extensions mutate it only through this holder. */
    internal val loggingState = ApplicationLoggingState()

    internal val ssid: String?
        get() = wifiContextForLogging().ssid

    internal fun wifiContextForLogging(): WifiContext {
        if (!loggingState.wifiLoggingEnabled || !canReadWifiContext()) return WifiContext.UNKNOWN
        refreshWifiContext()
        return loggingState.wifiContext
    }

    internal val vibratorManager: VibratorManager by lazy {
        getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
    }

    val launcherApps: LauncherApps by lazy {
        getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    }

    /** Settings collectors started by [startSettingsCollectors]; restarted after a rebind. */
    private var settingsCollectors: List<Job> = emptyList()

    init {
        startSettingsCollectors()
    }

    /** Restarts the settings collectors against the current [settingsRepository]. */
    private fun startSettingsCollectors() {
        settingsCollectors.forEach { it.cancel() }
        settingsCollectors = listOf(
            applicationScope.launch {
                settingsRepository.wifiLogging.collect {
                    if (it) enableSSIDLogging() else disableSSIDLogging()
                }
            },
            applicationScope.launch {
                settingsRepository.positionLogging.collect {
                    loggingState.setPositionLoggingEnabled(it)
                }
            }
        )
    }

    fun replaceDatabase(u: Uri) {
        this.database.close()
        this.contentResolver.openInputStream(u)?.use { input ->
            FileOutputStream(this@NeurhomeApplication.getDatabasePath(NEURHOME_DATABASE))
                .use { output -> input.copyTo(output) }
        }
        rebindDataLayer()
    }

    /**
     * Rebuilds every singleton bound to the database file that was just replaced. Room
     * keeps one database per process and never reopens a closed instance, and the
     * repositories hold DAOs of that instance, so they cannot serve the new file. The
     * relaunch that follows in `MainActivity` then builds fresh ViewModels on top of
     * these.
     */
    @Synchronized
    private fun rebindDataLayer() {
        if (repositoryDelegate.isInitialized()) repository.close()
        AppDatabase.forgetInstance()
        databaseDelegate = lazy { AppDatabase.getDatabase(this) }
        settingsRepositoryDelegate = lazy { createSettingsRepository() }
        favouritesRepositoryDelegate = lazy { createFavouritesRepository() }
        tagRepositoryDelegate = lazy { createTagRepository() }
        calendarRepositoryDelegate = lazy { createCalendarRepository() }
        repositoryDelegate = lazy { createRepository() }
        startSettingsCollectors()
    }

    fun getBattery() = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
        this.registerReceiver(null, filter)
    }
    override fun onCreate() {
        super.onCreate()
        // Debug-only launch/context capture. Never spawn a logcat process in
        // production builds; it keeps a process alive writing private data to
        // external storage and consumes battery and space.
        if (BuildConfig.DEBUG) {
            try {
                val logFile = java.io.File(getExternalFilesDir(null), "app_logcat.txt")
                Runtime.getRuntime()
                    .exec(arrayOf("logcat", "-f", logFile.absolutePath, "-v", "time"))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
