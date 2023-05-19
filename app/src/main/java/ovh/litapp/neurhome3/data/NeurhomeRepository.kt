package ovh.litapp.neurhome3.data

import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter

private const val TAG = "NeurhomeRepository"

class NeurhomeRepository(
    private val applicationLogEntryDao: ApplicationLogEntryDao,
    private val hiddenPackageDao: HiddenPackageDao,
    private val packageManager: PackageManager
) {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    @Suppress("DEPRECATION")
    val topApps: Flow<List<Application>> = applicationLogEntryDao.topApps().map { it ->
        Log.d(TAG, "Top apps")
        it.mapNotNull { packageName ->
            try {
                val app = packageManager.getApplicationInfo(packageName, PackageManager.MATCH_ALL)
                Application(
                    label = app.loadLabel(packageManager).toString(),
                    packageName = packageName,
                    packageManager.getApplicationIcon(packageName)
                )
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }.take(6)
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


    fun logLaunch(packageName: String) {
        coroutineScope.launch(Dispatchers.IO) {
            applicationLogEntryDao.insert(
                ApplicationLogEntry(
                    packageName = packageName, timestamp = DateTimeFormatter.ISO_INSTANT.format(
                        Instant.now()
                    )
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
}