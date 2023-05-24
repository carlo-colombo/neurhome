package ovh.litapp.neurhome3.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ovh.litapp.neurhome3.NeurhomeApplication
import ovh.litapp.neurhome3.ui.applications.AllApplicationsViewModel
import ovh.litapp.neurhome3.ui.home.HomeViewModel
import ovh.litapp.neurhome3.ui.settings.SettingsViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            val application = application()
            HomeViewModel(
                application.repository,
                application.packageManager,
                application::startActivity,
                application::vibrate,
                application::ssid,
                application::getPosition
            )
        }

        initializer {
            val application = application()

            AllApplicationsViewModel(
                application.repository,
                application.packageManager,
                application::startActivity,
                application::ssid,
                application::getPosition
            )
        }

        initializer {
            val application = application()
            SettingsViewModel(
                application.settingsRepository
            )
        }
    }
}

/**
 * Extension function to queries for [Application] object and returns an instance of
 * [NeurhomeApplication].
 */
fun CreationExtras.application(): NeurhomeApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NeurhomeApplication)