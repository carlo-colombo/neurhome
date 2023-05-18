package ovh.litapp.neurhome3.data

import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter

private const val TAG = "NeurhomeRepository"

class NeurhomeRepository(
    private val applicationLogEntryDao: ApplicationLogEntryDao,
    private val packageManager: PackageManager
) {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    @Suppress("DEPRECATION")
    val topApps: Flow<List<Application>> = applicationLogEntryDao.topApps().map { it ->
        Log.d(TAG, "Top apps")
        it.map { packageName ->
            val app = packageManager.getApplicationInfo(packageName, PackageManager.MATCH_ALL)
            Application(
                label = app.loadLabel(packageManager).toString(),
                packageName = packageName,
                packageManager.getApplicationIcon(packageName)
            )
        }.take(6)
    }

    @Suppress("DEPRECATION")
    val apps: Flow<List<Application>> = flow {
        Log.d(TAG, "Loading apps")

        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        emit(packageManager.queryIntentActivities(
            intent, PackageManager.MATCH_ALL
        ).map { app ->
            val packageName = app.activityInfo.packageName
            Application(
                label = app.loadLabel(packageManager).toString(),
                packageName = packageName,
                icon = packageManager.getApplicationIcon(packageName)
            )
        }.sortedBy { it.label.lowercase() })
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
}