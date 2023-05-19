package ovh.litapp.neurhome3.ui

import android.content.Intent
import android.content.Intent.ACTION_DELETE
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageManager
import android.net.Uri
import androidx.lifecycle.ViewModel
import ovh.litapp.neurhome3.data.NeurhomeRepository

abstract class NeurhomeViewModel(
    private val neurhomeRepository: NeurhomeRepository,
    private val packageManager: PackageManager,
    private val startActivity: (Intent) -> Unit,
) : ViewModel() {
    fun launch(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(
            packageName
        )
        if (intent != null) {
            startActivity(intent)
            neurhomeRepository.logLaunch(packageName)
        }
    }

    fun remove(packageName: String) {
        val intent = Intent(ACTION_DELETE)
        intent.flags = FLAG_ACTIVITY_NEW_TASK
        intent.data = Uri.parse("package:$packageName")
        startActivity(intent)
    }
}