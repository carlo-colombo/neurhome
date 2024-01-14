package ovh.litapp.neurhome3.data

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location
import android.net.Uri
import android.os.UserHandle
import android.util.Log
import androidx.core.database.getIntOrNull
import ch.hsr.geohash.GeoHash
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ovh.litapp.neurhome3.NeurhomeApplication
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val TAG = "NeurhomeRepository"


private const val DB_TO_IMPORT_NAME = "tmp_db_to_import"


class NeurhomeRepository(
    private val applicationLogEntryDao: ApplicationLogEntryDao,
    private val hiddenPackageDao: HiddenPackageDao,
    private val settingDao: SettingDao,
    private val packageManager: PackageManager,
    val application: NeurhomeApplication,
    val database: AppDatabase,
    val launcherApps: LauncherApps,
) {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val profiles = launcherApps.profiles.associateBy {
        it.hashCode()
    }

    fun getTopApps(n: Int = 6) = channelFlow {
        launch(Dispatchers.IO) {
            while (true) {
                send(applicationLogEntryDao.topApps().mapNotNull(::getApp).take(n))
                delay(java.time.Duration.ofSeconds(30).toMillis())
            }
        }
    }

    private fun getApp(packageCount: PackageCount): Application? {
        return try {
            val userHandle: UserHandle = profiles[packageCount.user] ?: profiles[0] ?: return null
            val intent = packageManager.getLaunchIntentForPackage(packageCount.packageName) ?: return null
            val launcherActivityInfo =
                launcherApps.resolveActivity(intent, userHandle) ?: return null

            Application(
                label = launcherActivityInfo.label.toString(),
                packageName = packageCount.packageName,
                icon = launcherActivityInfo.getBadgedIcon(0),
                count = packageCount.count,
                appInfo = launcherActivityInfo
            )
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun getApp(packageName: String): Application? {
        return getApp(PackageCount(packageName = packageName, count = 0, user = 0))
    }

    val apps: Flow<List<Application>> =
        applicationLogEntryDao.mostLoggedApp().map { packageCounts ->
            packageCounts.associate { it.packageName to it.count }
        }.combine(hiddenPackageDao.list()) { packages, hidden ->
            Log.d(TAG, "Loading apps")

            val intent = Intent(Intent.ACTION_MAIN, null)
            intent.addCategory(Intent.CATEGORY_LAUNCHER)

            launcherApps.profiles.flatMap { launcherApps.getActivityList(null, it) }
                .map { app ->
                    val packageName = app.activityInfo.packageName
                    Application(
                        label = app.label.toString(),
                        packageName = packageName,
                        icon = app.getBadgedIcon(0),
                        isVisible = !hidden.contains(packageName),
                        count = packages.getOrDefault(packageName, 0),
                        appInfo = app
                    )
                }.sortedBy { it.label.lowercase() }
        }

    fun logLaunch(
        activityInfo: LauncherActivityInfo,
        ssid: String?,
        position: Location?,
        query: String? = null
    ) {
        Log.d(TAG, "logLaunch: $activityInfo:$ssid:$position")
        coroutineScope.launch(Dispatchers.IO) {
            applicationLogEntryDao.insert(
                ApplicationLogEntry(
                    packageName = activityInfo.activityInfo.packageName,
                    timestamp =
                    DateTimeFormatter
                        .ISO_LOCAL_DATE_TIME
                        .withZone(ZoneId.systemDefault())
                        .format(Instant.now()),
                    wifi = ssid,
                    latitude = position?.latitude,
                    longitude = position?.longitude,
                    geohash = if (position != null) GeoHash.withCharacterPrecision(
                        position.latitude, position.longitude, 9
                    ).toBase32() else null,
                    user = activityInfo.user.hashCode(),
                    query = query
                )
            )
        }
    }

    fun toggleVisibility(packageName: String) {
        val hiddenPackage = HiddenPackage(packageName = packageName)
        coroutineScope.launch(Dispatchers.IO) {
            try {
                hiddenPackageDao.insert(hiddenPackage)
            } catch (e: SQLiteConstraintException) {
                Log.d(TAG, e.toString())
                try {
                    hiddenPackageDao.delete(hiddenPackage)
                } catch (e: Exception) {
                    Log.d(TAG, e.toString())
                    throw e
                }
            }
        }
    }

    val favouriteApps = settingDao.like("favourites.%").map { favourites ->
        val favouritesMap = favourites.associate {
            it.key to it.value
        }
        arrayOf(1, 2, 3, 4).mapNotNull { i ->
            favouritesMap["favourites.$i"]?.let { packageName ->
                getApp(packageName)?.let {
                    i to it
                }
            }
        }.toMap()
    }

    fun setFavourite(packageName: String, index: Int) {
        coroutineScope.launch(Dispatchers.IO) {
            settingDao.insertOverride(Setting(key = "favourites.$index", packageName))
        }
    }

    fun insertFromDB(u: Uri?) {
        val databasePath = application.getDatabasePath(DB_TO_IMPORT_NAME)
        try {
            application.contentResolver.openInputStream(u!!)?.use {
                it.copyTo(
                    FileOutputStream(databasePath)
                )
            }

            sqLiteOpenHelper.use { db ->
                val s = sequence {
                    val cursor =
                        db.readableDatabase.query(/* table = */ "application_log",/* columns = */
                            arrayOf(
                                "package",
                                "timestamp",
                                "wifi",
                                "latitude",
                                "longitude",
                                "geohash",
                                "user"
                            ),/* selection = */
                            "",/* selectionArgs = */
                            arrayOf(),/* groupBy = */
                            null,/* having = */
                            null,/* orderBy = */
                            ""
                        )

                    with(cursor) {
                        while (moveToNext()) {
                            this@sequence.yield(
                                ApplicationLogEntry(
                                    packageName = getString(0),
                                    timestamp = getString(1),
                                    wifi = getString(2),
                                    latitude = getDouble(3),
                                    longitude = getDouble(4),
                                    geohash = getString(5),
                                    user = getIntOrNull(6) ?: 0
                                )
                            )
                        }
                    }
                }

                database.runInTransaction {
                    applicationLogEntryDao.cleanUp()
                    s.chunked(1000).forEach { entries ->
                        applicationLogEntryDao.insertAll(entries)
                        Log.d(TAG, "${entries.size} entries inserted")
                    }
                }
            }

        } catch (e: Exception) {
            Log.d(TAG, "Error happened while importing: $e")
        } finally {
            if (databasePath.exists()) databasePath.delete()
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

    private val sqLiteOpenHelper by lazy {
        (object : SQLiteOpenHelper(application, DB_TO_IMPORT_NAME, null, 5) {
            override fun onCreate(db: SQLiteDatabase?) {}
            override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {}
        })
    }
}