package ovh.litapp.neurhome3.application

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ovh.litapp.neurhome3.ui.NeurhomeViewModelDependencies
import ovh.litapp.neurhome3.ui.applications.AllApplicationsViewModel
import ovh.litapp.neurhome3.ui.categories.CategoryViewModel
import ovh.litapp.neurhome3.ui.home.HomeViewModel
import ovh.litapp.neurhome3.ui.settings.SettingsViewModel
import ovh.litapp.neurhome3.ui.stats.AppStatisticsViewModel

/**
 * Composition-root module for ViewModel creation: the single place that maps
 * [NeurhomeApplication]'s repositories, services and logging helpers onto each
 * ViewModel's dependency graph. Screens only reference [Factory] and never build
 * dependencies themselves.
 *
 * Graphs are assembled per call instead of being cached, because
 * [NeurhomeApplication.rebindDataLayer] replaces the repositories after the
 * database file is swapped and new ViewModels must see the rebuilt instances.
 */
object AppViewModelFactory {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer { homeViewModel(application()) }
        initializer { allApplicationsViewModel(application()) }
        initializer { categoryViewModel(application()) }
        initializer { settingsViewModel(application()) }
        initializer { appStatisticsViewModel(application()) }
    }
}

private fun CreationExtras.application(): NeurhomeApplication =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NeurhomeApplication

/** The dependencies shared by every [ovh.litapp.neurhome3.ui.NeurhomeViewModel] subclass. */
private fun sharedViewModelDependencies(app: NeurhomeApplication) = NeurhomeViewModelDependencies(
    neurhomeRepository = app.repository,
    favouritesRepository = app.favouritesRepository,
    startActivity = app::startActivity,
    getWifiContext = app::wifiContextForLogging,
    getPositionForLogging = app::getPositionForLogging,
    launcherApps = app.launcherApps,
    checkPermission = app::checkPermission,
)

private fun homeViewModel(app: NeurhomeApplication) = HomeViewModel(
    dependencies = sharedViewModelDependencies(app),
    settingsRepository = app.settingsRepository,
    calendarRepository = app.calendarRepository,
    clockAlarmRepository = app.alarmRepository,
    weatherRepository = app.weatherRepository,
    vibrate = app::vibrate,
    getPositionForUi = app::getPosition,
    getCityName = app::getCityName,
    getBattery = app::getBattery,
)

private fun allApplicationsViewModel(app: NeurhomeApplication) = AllApplicationsViewModel(
    dependencies = sharedViewModelDependencies(app),
    tagRepository = app.tagRepository,
)

private fun categoryViewModel(app: NeurhomeApplication) = CategoryViewModel(
    tagRepository = app.tagRepository,
    dependencies = sharedViewModelDependencies(app),
)

private fun settingsViewModel(app: NeurhomeApplication) = SettingsViewModel(
    settingsRepository = app.settingsRepository,
    neurhomeRepository = app.repository,
)

private fun appStatisticsViewModel(app: NeurhomeApplication) = AppStatisticsViewModel(
    neurhomeRepository = app.repository,
)
