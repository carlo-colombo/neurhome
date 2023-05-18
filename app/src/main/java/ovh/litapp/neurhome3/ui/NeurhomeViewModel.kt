package ovh.litapp.neurhome3.ui

import android.content.Intent
import android.content.pm.PackageManager
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
}