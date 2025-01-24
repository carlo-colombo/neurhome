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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ovh.litapp.neurhome3.ApplicationService
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
    private val applicationService: ApplicationService,
    private val contactsDAO: ContactsDAO,
    val application: NeurhomeApplication,
    val database: AppDatabase,
    val launcherApps: LauncherApps,
) {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private val ticker = flow {
        while (true) {
            emit(42)
            delay(Duration.ofMinutes(5).toMillis())
        }
    }
    private val contacts =
        combine(ticker, application.settingsRepository.showStarredContacts) { _, showContacts ->
            if (!showContacts || !application.checkPermission(Manifest.permission.READ_CONTACTS)) {
                return@combine listOf()
            }

            contactsDAO.getStarredContacts()
        }

    private val allApps = combine(ticker, hiddenPackageDao.list()) { _, hidden ->
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
                appInfo = app,
                intent = null
            )
        }
    }

    private val packageFrequency = applicationLogEntryDao.mostLoggedApp().map { packageCounts ->
        packageCounts.associate { it.packageName to it.count }
    }

    val applicationAndContacts: Flow<List<Application>> =
        combine(packageFrequency, contacts, allApps) { packageFrequency, contacts, allApps ->
            (contacts.map { a ->
                a.copy(count = packageFrequency[a.intent?.data.toString()] ?: 0)
            } + allApps.map { a ->
                a.copy(count = packageFrequency[a.packageName] ?: 0)
            }).sortedBy { it.label.lowercase() }
        }.flowOn(Dispatchers.IO)

    fun getTopApps(n: Int = 6) = flow {
        while (true) {
            emit(
                applicationLogEntryDao
                    .topApps()
                    .asSequence()
                    .mapNotNull(applicationService::toApplication)
                    .take(n)
                    .toList()
            )
            delay(Duration.ofSeconds(30).toMillis())
        }
    }.flowOn(Dispatchers.IO)

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
}
