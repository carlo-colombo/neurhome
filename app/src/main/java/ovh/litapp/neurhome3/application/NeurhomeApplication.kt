package ovh.litapp.neurhome3.application

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Uri
import android.os.UserManager
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ovh.litapp.neurhome3.ApplicationService
import ovh.litapp.neurhome3.data.AppDatabase
import ovh.litapp.neurhome3.data.NEURHOME_DATABASE
import ovh.litapp.neurhome3.data.dao.CalendarDAO
import ovh.litapp.neurhome3.data.dao.ContactsDAO
import ovh.litapp.neurhome3.data.repositories.CalendarRepository
import ovh.litapp.neurhome3.data.repositories.ClockAlarmRepository
import ovh.litapp.neurhome3.data.repositories.FavouritesRepository
import ovh.litapp.neurhome3.data.repositories.NeurhomeRepository
import ovh.litapp.neurhome3.data.repositories.SettingsRepository
import java.io.FileOutputStream


class NeurhomeApplication : Application() {
    // No need to cancel this scope as it'll be torn down with the process
    private val applicationScope = CoroutineScope(SupervisorJob())

    init {
        applicationScope.launch {
            settingsRepository.wifiLogging.collect {
                if (it) enableSSIDLogging() else disableSSIDLogging()
            }
        }
    }

    private val database by lazy {
        AppDatabase.getDatabase(this)
    }

    private val applicationService by lazy {
        ApplicationService(
            packageManager = packageManager,
            launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
        )
    }

    val repository by lazy {
        NeurhomeRepository(
            applicationLogEntryDao = database.applicationLogEntryDao(),
            additionalPackageMetadataDao = database.hiddenPackageDao(),
            contactsDAO = ContactsDAO(this),
            application = this,
            database = database,
            launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps,
            applicationService = applicationService,
            userManager = getSystemService(Context.USER_SERVICE) as UserManager
        )
    }

    val favouritesRepository by lazy {
        FavouritesRepository(
            settingDao = database.settingDao(),
            applicationService = applicationService
        )
    }

    val settingsRepository by lazy {
        SettingsRepository(database.settingDao())
    }

    val calendarRepository by lazy {
        CalendarRepository(::checkPermission, settingsRepository, CalendarDAO(this))
    }

    val alarmRepository by lazy {
        ClockAlarmRepository(this.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
    }

    fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            this, permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    var ssid: String? = null
        internal set

    internal var cb: NetworkCallback? = null

    internal val vibratorManager: VibratorManager by lazy {
        getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
    }

    val launcherApps: LauncherApps by lazy {
        getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    }

    fun replaceDatabase(u: Uri) {
        this.database.close()
        this.contentResolver.openInputStream(u)?.use {
            it.copyTo(
                FileOutputStream(this@NeurhomeApplication.getDatabasePath(NEURHOME_DATABASE))
            )
        }
    }

    fun getBattery() = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
        this.registerReceiver(null, filter)
    }
}