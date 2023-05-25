package ovh.litapp.neurhome3.ui

import android.content.Intent
import android.content.Intent.ACTION_DELETE
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import androidx.lifecycle.ViewModel
import ovh.litapp.neurhome3.data.NeurhomeRepository

abstract class NeurhomeViewModel(
    private val neurhomeRepository: NeurhomeRepository,
    private val packageManager: PackageManager,
    private val startActivity: (Intent) -> Unit,
    private val getSSID: () -> String?,
    private val getPosition: () -> Location?
) : ViewModel() {
    open fun launch(packageName: String, track: Boolean = true) {
        val intent = packageManager.getLaunchIntentForPackage(
            packageName
        )
        if (intent != null) {
            startActivity(intent)
            if (track) {
                neurhomeRepository.logLaunch(packageName, getSSID(), getPosition())
            }
        }
    }

    private fun remove(packageName: String) {
        val intent = Intent(ACTION_DELETE)
        intent.flags = FLAG_ACTIVITY_NEW_TASK
        intent.data = Uri.parse("package:$packageName")
        startActivity(intent)
    }

    private fun toggleVisibility(packageName: String) {
        neurhomeRepository.toggleVisibility(packageName)
    }

    private fun setFavourite(packageName: String, index: Int) =
        neurhomeRepository.setFavourite(packageName, index)

    val appActions =
        AppActions(remove = ::remove, launch = ::launch, ::toggleVisibility, ::setFavourite)

    data class AppActions(
        val remove: (String) -> Unit = {},
        val launch: (String) -> Unit = {},
        val toggleVisibility: (String) -> Unit = {},
        val setFavourite: (String, Int) -> Unit = { _, _ -> }
    )
}