package ovh.litapp.neurhome3.ui

import android.content.Intent
import android.content.Intent.ACTION_DELETE
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import ovh.litapp.neurhome3.data.NeurhomeRepository

abstract class NeurhomeViewModel(
    private val neurhomeRepository: NeurhomeRepository,
    private val packageManager: PackageManager,
    private val startActivity: (Intent) -> Unit,
    private val getSSID: () -> String?
) : ViewModel() {
    open fun launch(packageName: String) {
        Log.d("NeurhomeViewModel", "- ${getSSID()}  -")

        val intent = packageManager.getLaunchIntentForPackage(
            packageName
        )
        if (intent != null) {
            startActivity(intent)
            neurhomeRepository.logLaunch(packageName, getSSID())
        }
    }

    fun remove(packageName: String) {
        val intent = Intent(ACTION_DELETE)
        intent.flags = FLAG_ACTIVITY_NEW_TASK
        intent.data = Uri.parse("package:$packageName")
        startActivity(intent)
    }

    fun toggleVisibility(packageName: String) {
        neurhomeRepository.toggleVisibility(packageName)
    }

    fun setFavourite(packageName: String, index: Int) =
        neurhomeRepository.setFavourite(packageName, index)
}