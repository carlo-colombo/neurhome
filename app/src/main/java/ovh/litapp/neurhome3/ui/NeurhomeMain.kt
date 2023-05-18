package ovh.litapp.neurhome3.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ovh.litapp.neurhome3.Navigator
import ovh.litapp.neurhome3.Navigator.NavTarget.ApplicationList
import ovh.litapp.neurhome3.Navigator.NavTarget.Home
import ovh.litapp.neurhome3.ui.applications.AllApplicationsScreen
import ovh.litapp.neurhome3.ui.home.HomeScreen

@Composable
fun NeurhomeMain(
) {
    val navController = rememberNavController()

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
            HomeScreen(onAppsClick = { Navigator.navigateTo(ApplicationList) })
        }
        composable(ApplicationList.label) {
            AllApplicationsScreen()
        }
    }
}
