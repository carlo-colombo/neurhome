package ovh.litapp.neurhome3.data.repositories

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.location.Location
import android.os.UserHandle
import android.util.Log
import ch.hsr.geohash.GeoHash
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ovh.litapp.neurhome3.application.NeurhomeApplication
import ovh.litapp.neurhome3.data.AppDatabase
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.ApplicationLogEntry
import ovh.litapp.neurhome3.data.HiddenPackage
import ovh.litapp.neurhome3.data.NeurhomeFileProvider
import ovh.litapp.neurhome3.data.Setting
import ovh.litapp.neurhome3.data.dao.ApplicationLogEntryDao
import ovh.litapp.neurhome3.data.dao.ContactsDAO
import ovh.litapp.neurhome3.data.dao.HiddenPackageDao
import ovh.litapp.neurhome3.data.dao.SettingDao
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter


private const val TAG = "NeurhomeRepository"

class NeurhomeRepository(
    private val applicationLogEntryDao: ApplicationLogEntryDao,
    private val hiddenPackageDao: HiddenPackageDao,
    private val settingDao: SettingDao,
    private val packageManager: PackageManager,
    private val contactsDAO: ContactsDAO,
    val application: NeurhomeApplication,
    val database: AppDatabase,
    val launcherApps: LauncherApps,
) {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val profiles = launcherApps.profiles.associateBy { it.hashCode() }
    private val contacts = flow {
        while (true) {
            emit(42)
            delay(Duration.ofMinutes(5).toMillis())
        }
    }.combine(application.settingsRepository.showStarredContacts) { _, showCalendar ->
        if (!showCalendar || !application.checkPermission(Manifest.permission.READ_CONTACTS)) {
            return@combine listOf()
        }

        contactsDAO.getStarredContacts()
    }

    val apps: Flow<List<Application>> =
        applicationLogEntryDao.mostLoggedApp().map { packageCounts ->
            packageCounts.associate { it.packageName to it.count }
        }.combine(hiddenPackageDao.list()) { packages, hidden ->
            Log.d(TAG, "Loading apps")

            launcherApps.profiles.flatMap { launcherApps.getActivityList(null, it) }.map { app ->
                    val packageName = app.activityInfo.packageName
                    Application(
                        label = app.label.toString(),
                        packageName = packageName,
                        icon = app.getBadgedIcon(0),
                        isVisible = !hidden.contains(
                            HiddenPackage(
                                packageName, app.user.hashCode()
                            )
                        ),
                        count = packages.getOrDefault(packageName, 0),
                        appInfo = app,
                        intent = null
                    )
                }.sortedBy { it.label.lowercase() }
        }.combine(contacts) { apps, contacts -> apps + contacts }.flowOn(Dispatchers.IO)


    fun getTopApps(n: Int = 6) = channelFlow {
        launch(Dispatchers.IO) {
            while (true) {
                send(applicationLogEntryDao.topApps().mapNotNull(::toApplication).take(n))
                delay(Duration.ofSeconds(30).toMillis())
            }
        }
    }

    val favouriteApps = settingDao.like("favourites.%").flowOn(Dispatchers.IO).map { favourites ->
        val favouritesMap = favourites.associate { it.key to it.value }
        arrayOf(1, 2, 3, 4).mapNotNull { i ->
            favouritesMap["favourites.$i"]?.let { packageName ->
                toApplication(packageName)?.let {
                    i to it
                }
            }
        }.toMap()
    }

    fun logLaunch(
        packageName: String,
        user: Int,
        ssid: String?,
        position: Location?,
        query: String? = null
    ) {
        Log.d(TAG, "logLaunch: $packageName:$ssid:$position")

        coroutineScope.launch(Dispatchers.IO) {
            applicationLogEntryDao.insert(
                ApplicationLogEntry(
                    packageName = packageName,
                    timestamp = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault())
                        .format(Instant.now()),
                    wifi = ssid,
                    latitude = position?.latitude,
                    longitude = position?.longitude,
                    geohash = if (position != null) GeoHash.withCharacterPrecision(
                        position.latitude, position.longitude, 9
                    ).toBase32() else null,
                    user = user,
                    query = query
                )
            )
        }
    }

    fun toggleVisibility(application: Application) {
        val hiddenPackage = HiddenPackage(
            packageName = application.packageName, application.appInfo?.user.hashCode()
        )

        coroutineScope.launch(Dispatchers.IO) {
            hiddenPackageDao.toggle(hiddenPackage)
        }
    }

    fun setFavourite(application: Application, index: Int) {
        coroutineScope.launch(Dispatchers.IO) {
            settingDao.insertOverride(Setting(key = "favourites.$index", application.packageName))
        }
    }

    fun exportDatabase(context: Context) {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "application/octet-stream"

        val cursor = database.query("pragma wal_checkpoint(full)", arrayOf())

        cursor.moveToFirst()

        val uri = NeurhomeFileProvider().getDatabaseURI(context)

        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.flags = FLAG_GRANT_READ_URI_PERMISSION

        context.startActivity(Intent.createChooser(intent, "Backup via:"))
    }

    private fun toApplication(packageCount: ApplicationLogEntryDao.PackageCount): Application? {
        return try {
            val userHandle: UserHandle = profiles[packageCount.user] ?: profiles[0] ?: return null
            val intent =
                packageManager.getLaunchIntentForPackage(packageCount.packageName) ?: return null
            val launcherActivityInfo =
                launcherApps.resolveActivity(intent, userHandle) ?: return null

            Application(
                label = launcherActivityInfo.label.toString(),
                packageName = packageCount.packageName,
                icon = launcherActivityInfo.getBadgedIcon(0),
                count = packageCount.count,
                appInfo = launcherActivityInfo,
                intent = null
            )
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun toApplication(packageName: String): Application? = toApplication(
        ApplicationLogEntryDao.PackageCount(
            packageName = packageName, count = 0, user = 0
        )
    )
}
