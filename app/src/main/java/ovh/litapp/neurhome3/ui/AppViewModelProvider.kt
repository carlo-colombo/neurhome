package ovh.litapp.neurhome3.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ovh.litapp.neurhome3.application.NeurhomeApplication
import ovh.litapp.neurhome3.application.getPosition
import ovh.litapp.neurhome3.application.vibrate
import ovh.litapp.neurhome3.ui.applications.AllApplicationsViewModel
import ovh.litapp.neurhome3.ui.home.HomeViewModel
import ovh.litapp.neurhome3.ui.settings.SettingsViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            val application = application()

            HomeViewModel(
                application.repository,
                application.favouritesRepository,
                application.settingsRepository,
                application.calendarRepository,
                application.alarmRepository,
                application::startActivity,
                application::vibrate,
                application::ssid,
                application::getPosition,
                application.launcherApps,
                application::checkPermission,
                application::getBattery
            )
        }

        initializer {
            val application = application()

            AllApplicationsViewModel(
                application.repository,
                application.favouritesRepository,
                application::startActivity,
                application::ssid,
                application::getPosition,
                application.launcherApps,
                application::checkPermission
            )
        }

        initializer {
            val application = application()
            SettingsViewModel(
                application.settingsRepository,
                application.repository
            )
        }
    }
}

/**
 * Extension function to queries for [NeurhomeApplication] object and returns an instance of
 * [NeurhomeApplication].
 */
fun CreationExtras.application(): NeurhomeApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NeurhomeApplication)