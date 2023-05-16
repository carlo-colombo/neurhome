package ovh.litapp.neurhome3.data

import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import ovh.litapp.neurhome3.ui.Application
import java.time.Instant
import java.time.format.DateTimeFormatter

private const val TAG = "NeurhomeRepository"

class NeurhomeRepository(
    private val applicationLogEntryDao: ApplicationLogEntryDao,
    private val packageManager: PackageManager
) {
    val topApps: Flow<List<ApplicationLogEntry>> = applicationLogEntryDao.topApps(6)

    @Suppress("DEPRECATION")
    fun apps(): List<Application> {
        Log.d(TAG, "Loading apps")
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        return packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_ALL
        ).map { app ->
            val packageName = app.activityInfo.packageName
            Application(
                label = app.loadLabel(packageManager).toString(),
                packageName = packageName,
                icon = packageManager.getApplicationIcon(packageName)
            )
        }.sortedBy { it.label.lowercase() }
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
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