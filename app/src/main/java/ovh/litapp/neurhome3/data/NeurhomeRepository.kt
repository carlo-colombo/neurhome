package ovh.litapp.neurhome3.data

import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ovh.litapp.neurhome3.NeurhomeApplication
import ovh.litapp.neurhome3.ui.applications.ImportingDB
import java.io.FileOutputStream
import java.time.Instant
import java.time.format.DateTimeFormatter

private const val TAG = "NeurhomeRepository"

class NeurhomeRepository(
    private val applicationLogEntryDao: ApplicationLogEntryDao,
    private val hiddenPackageDao: HiddenPackageDao,
    private val settingDao: SettingDao,
    private val packageManager: PackageManager,
    val application: NeurhomeApplication,
    val database: AppDatabase,
) {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    val topApps: Flow<List<Application>> = applicationLogEntryDao.topApps().map { it ->
        Log.d(TAG, "Top apps")
        it.mapNotNull(::getApp).take(6)
    }

    @Suppress("DEPRECATION")
    private fun getApp(packageName: String?): Application? {
        if (packageName == null) return null
        return try {
            val app = packageManager.getApplicationInfo(packageName, PackageManager.MATCH_ALL)
            Application(
                label = app.loadLabel(packageManager).toString(),
                packageName = packageName,
                packageManager.getApplicationIcon(packageName)
            )
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    @Suppress("DEPRECATION")
    val apps: Flow<List<Application>> = hiddenPackageDao.list().map { hidden ->
        Log.d(TAG, "Loading apps")

        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        packageManager.queryIntentActivities(
            intent, PackageManager.MATCH_ALL
        ).map { app ->
            val packageName = app.activityInfo.packageName
            Application(
                label = app.loadLabel(packageManager).toString(),
                packageName = packageName,
                icon = packageManager.getApplicationIcon(packageName),
                isVisible = !hidden.contains(packageName)
            )
        }.sortedBy { it.label.lowercase() }
    }


    fun logLaunch(packageName: String, ssid: String?) {
        coroutineScope.launch(Dispatchers.IO) {
            applicationLogEntryDao.insert(
                ApplicationLogEntry(
                    packageName = packageName, timestamp = DateTimeFormatter.ISO_INSTANT.format(
                        Instant.now()
                    ), wifi = ssid
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
            getApp(favouritesMap["favourites.$i"])?.let {
                i to it
            }
        }.toMap()
    }

    fun setFavourite(packageName: String, index: Int) {
        coroutineScope.launch(Dispatchers.IO) {
            settingDao.insertOverride(Setting(key = "favourites.$index", packageName))
        }
    }

    fun insertFromDB(u: Uri?) {
        val databasePath = application.getDatabasePath("tmp_db_to_import")
        try {

            application.contentResolver.openInputStream(u!!)?.copyTo(
                FileOutputStream(databasePath)
            )
            ImportingDB(application, "db_to_import").use { db ->
                val s = sequence {
                    val cursor =
                        db.readableDatabase.query(
                            /* table = */ "application_log",
                            /* columns = */arrayOf("package", "timestamp", "wifi"),
                            /* selection = */ "",
                            /* selectionArgs = */ arrayOf(),
                            /* groupBy = */ null,
                            /* having = */ null,
                            /* orderBy = */ ""
                        )

                    with(cursor) {
                        while (moveToNext()) {
                            this@sequence.yield(
                                ApplicationLogEntry(
                                    packageName = getString(0),
                                    timestamp = getString(1),
                                    wifi = getString(2)
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
}