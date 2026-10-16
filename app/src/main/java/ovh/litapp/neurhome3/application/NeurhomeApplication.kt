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
import android.util.Log
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
import ovh.litapp.neurhome3.data.NEURHOME_DATABASE_VERSION
import ovh.litapp.neurhome3.data.NeurhomeFileProvider
import ovh.litapp.neurhome3.data.dao.CalendarDAO
import ovh.litapp.neurhome3.data.dao.ContactsDAO
import ovh.litapp.neurhome3.data.repositories.CalendarRepository
import ovh.litapp.neurhome3.data.repositories.ClockAlarmRepository
import ovh.litapp.neurhome3.data.repositories.FavouritesRepository
import ovh.litapp.neurhome3.data.repositories.LogRetention
import ovh.litapp.neurhome3.data.repositories.NeurhomeRepository
import ovh.litapp.neurhome3.data.repositories.RoomHomeAppRankingSource
import ovh.litapp.neurhome3.data.repositories.SettingsRepository
import ovh.litapp.neurhome3.data.repositories.TagRepository
import ovh.litapp.neurhome3.data.weather.WeatherRepository
import ovh.litapp.neurhome3.data.weather.WeatherServiceImpl
import ovh.litapp.neurhome3.data.models.WifiContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val DATABASE_SWAP_LOG_TAG = "NeurhomeDatabaseSwap"


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
            launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps,
            userManager = getSystemService(Context.USER_SERVICE) as UserManager
        )
    }

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

    /**
     * Cancels the settings collectors so they cannot query the database while it is
     * closed for a swap. [startSettingsCollectors] restarts them afterwards.
     */
    private fun stopSettingsCollectors() {
        settingsCollectors.forEach { it.cancel() }
        settingsCollectors = emptyList()
    }

    /** Restarts the settings collectors against the current [settingsRepository]. */
    private fun startSettingsCollectors() {
        stopSettingsCollectors()
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

    /**
     * Replaces the database file with the user-picked [uri], but only after the candidate
     * is proven usable. The picked file is streamed into a temp file next to the live
     * database while the live database stays open, then validated, and only a valid
     * candidate is swapped in. On any failure the live database is left untouched and the
     * caller gets a [DatabaseReplacementResult] to surface to the user.
     *
     * A successful swap closes the data layer, so the caller must relaunch the UI; a failed
     * swap reopens the untouched database in the same process.
     */
    fun replaceDatabase(uri: Uri): DatabaseReplacementResult {
        val liveDatabase = getDatabasePath(NEURHOME_DATABASE)
        val databasesDirectory = liveDatabase.parentFile
            ?: return DatabaseReplacementResult.IoError
        val candidate = try {
            File.createTempFile("$NEURHOME_DATABASE-candidate-", ".tmp", databasesDirectory)
        } catch (e: IOException) {
            Log.w(DATABASE_SWAP_LOG_TAG, "Could not stage a database candidate", e)
            return DatabaseReplacementResult.IoError
        }
        if (!streamIntoCandidate(uri, candidate)) {
            candidate.delete()
            return DatabaseReplacementResult.IoError
        }
        when (val validation =
            DatabaseCandidateValidator.validate(candidate, NEURHOME_DATABASE_VERSION)) {
            DatabaseCandidateValidation.NotADatabase -> {
                candidate.delete()
                return DatabaseReplacementResult.NotADatabase
            }
            is DatabaseCandidateValidation.UnsupportedVersion -> {
                candidate.delete()
                return DatabaseReplacementResult.UnsupportedVersion(validation.schemaVersion)
            }
            DatabaseCandidateValidation.Valid -> Unit
        }
        return swapIn(candidate, liveDatabase)
    }

    /** Copies the picked stream into [candidate]; [candidate] is the caller's to delete. */
    private fun streamIntoCandidate(uri: Uri, candidate: File): Boolean = try {
        contentResolver.openInputStream(uri)?.use { input ->
            candidate.outputStream().use { output -> input.copyTo(output) }
            true
        } ?: false
    } catch (e: IOException) {
        Log.w(DATABASE_SWAP_LOG_TAG, "Could not read the picked database file", e)
        false
    } catch (e: SecurityException) {
        Log.w(DATABASE_SWAP_LOG_TAG, "No permission to read the picked database file", e)
        false
    }

    /**
     * Swaps a validated [candidate] in for [liveDatabase]. Ordering is deliberate: the
     * repositories' eager flows are stopped before the database is closed (BUG-05), the
     * move is atomic so a crash cannot leave a half-written live file, stale sidecars that
     * belong to the replaced file are removed so they cannot be replayed over the new one,
     * and only then is [rebindDataLayer] run.
     */
    private fun swapIn(candidate: File, liveDatabase: File): DatabaseReplacementResult {
        if (repositoryDelegate.isInitialized()) repository.close()
        stopSettingsCollectors()
        database.close()
        val moved = try {
            Files.move(
                candidate.toPath(),
                liveDatabase.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
            true
        } catch (e: IOException) {
            Log.w(DATABASE_SWAP_LOG_TAG, "Could not move the validated candidate into place", e)
            false
        }
        if (moved) deleteStaleSidecars(liveDatabase) else candidate.delete()
        // Rebuild the data layer either way: on failure the untouched live file is reopened
        // so the app keeps working instead of staying on a closed database.
        rebindDataLayer()
        return if (moved) DatabaseReplacementResult.Replaced else DatabaseReplacementResult.IoError
    }

    /** Removes write-ahead/rollback sidecars left by the replaced database file. */
    private fun deleteStaleSidecars(liveDatabase: File) {
        for (suffix in listOf("-wal", "-shm", "-journal")) {
            File(liveDatabase.path + suffix).delete()
        }
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

    /**
     * Fire-and-forget launch-log pruning. It reads [database] directly instead of going through a
     * repository lazy, so it depends on no other lazy being initialized first; the retention pass
     * self-limits to once a day on IO and is a no-op otherwise.
     */
    private fun scheduleLogRetention() {
        LogRetention(
            logEntryDao = database.applicationLogEntryDao(),
            settingDao = database.settingDao(),
            database = database,
        ).pruneInBackground(applicationScope)
    }

    override fun onCreate() {
        super.onCreate()
        // Start here, not in an `init` block: collectors touch [settingsRepository], whose
        // [database] calls `context.applicationContext`. That is null until the framework has
        // attached the base context, and construction-time launches could win that race and
        // crash on some devices.
        startSettingsCollectors()
        scheduleLogRetention()
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
