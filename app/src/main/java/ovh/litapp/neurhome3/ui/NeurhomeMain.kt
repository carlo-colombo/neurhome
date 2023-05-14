package ovh.litapp.neurhome3.ui

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ovh.litapp.neurhome3.Navigator
import ovh.litapp.neurhome3.Navigator.NavTarget.ApplicationList
import ovh.litapp.neurhome3.Navigator.NavTarget.Home
import kotlin.reflect.KFunction0

@Composable
fun NeurhomeMain(
    packageManager: PackageManager,
    startActivity: (Intent) -> Unit = {},
    vibrate: KFunction0<Unit>,
    appsViewModel: ApplicationsViewModel = viewModel(
        factory = ApplicationsViewModelFactory(packageManager, startActivity, vibrate)
    )
) {
    val navController = rememberNavController()
    val appsUiState by appsViewModel.uiState.collectAsState()

    LaunchedEffect("navigation") {
        Navigator.sharedFlow.onEach {
            navController.navigate(it.label)
        }.launchIn(this)
    }

    NavHost(
        navController = navController,
        startDestination = Home.label
    ) {
        composable(Home.label) {
            Home(appsUiState=appsUiState, appsViewModel=appsViewModel,onAppsClick = { Navigator.navigateTo(ApplicationList) })
        }
        composable(ApplicationList.label) {
            ApplicationList(appsUiState, appsViewModel)
        }
    }
}

class ApplicationsViewModelFactory(
    private val packageManager: PackageManager,
    private val startActivity: (Intent) -> Unit,
    private val vibrate: () -> Unit
) :
    ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ApplicationsViewModel(
            packageManager = packageManager,
            startActivity = startActivity,
            vibrate = vibrate
        ) as T
    }
}