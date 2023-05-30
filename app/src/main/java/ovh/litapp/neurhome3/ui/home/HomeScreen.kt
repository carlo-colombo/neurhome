package ovh.litapp.neurhome3.ui.home

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import ovh.litapp.neurhome3.R
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.Event
import ovh.litapp.neurhome3.ui.AppViewModelProvider
import ovh.litapp.neurhome3.ui.INeurhomeViewModel
import ovh.litapp.neurhome3.ui.components.Calendar
import ovh.litapp.neurhome3.ui.components.Keyboard
import ovh.litapp.neurhome3.ui.components.Watch
import ovh.litapp.neurhome3.ui.theme.Neurhome3Theme
import java.time.LocalDateTime

private const val TAG = "HomeScreen"

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val homeUiState by viewModel.homeUiState.collectAsState()
    Log.d(TAG, "$homeUiState")

    Home(navController = navController, viewModel = viewModel, homeUiState = homeUiState)
}


@Composable
fun Home(navController: NavController, viewModel: IHomeViewModel, homeUiState: HomeUiState) {
    BackHandler(true) { viewModel.clearQuery() }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(modifier = Modifier.weight(1.2f)) {
            Watch(viewModel::openAlarms)
        }

        Box(modifier = Modifier.weight(1f, true), contentAlignment = Alignment.Center) {
            if (homeUiState.showCalendar) {
                Calendar(
                    list = homeUiState.events,
                    openEvent = viewModel::openCalendar
                )
            }
        }

        Column(verticalArrangement = Arrangement.Bottom, modifier = Modifier.weight(6f, true)) {
            Box(modifier = Modifier.weight(6f, true)) {
                HomeApplicationsList(
                    list = homeUiState.homeApps,
                    appActions = viewModel.appActions
                )
            }
            Box(modifier = Modifier.weight(3f, true)) {
                Keyboard(appsViewModel = viewModel, appsUiState = homeUiState)
            }
            Box(modifier = Modifier.weight(1f, true)) {
                BottomBar(homeUiState, viewModel, navController)
            }
        }
    }
}

@Composable
@Preview(backgroundColor = 0x00000)
fun HomePreview() {
    Neurhome3Theme() {
        val drawable =
            AppCompatResources.getDrawable(LocalContext.current, R.drawable.ic_launcher_foreground)
        Home(
            navController = rememberNavController(), viewModel = object : IHomeViewModel {
                override fun push(s: String) {}
                override fun clearQuery() {}
                override fun pop() {}
                override fun openAlarms() {}

                override val vibrate = {}
                override fun openCalendar(event: Event) {}

                override val appActions = INeurhomeViewModel.AppActions()

            }, homeUiState = HomeUiState(
                homeApps = drawable?.let {
                    listOf(
                        Application("Fooasd foofasd asdoasod", "net.fofvar", icon = it),
                        Application("Fooasd", "net.fofvar.klarna", icon = it),
                        Application("Fooasd", "net.fofvar.klarna", icon = it),
                        Application("Fooasd", "net.fofvar.klarna", icon = it),
                        Application("Fooasd", "net.fofvar.barzot", icon = it),
                        Application("Fooasd", "net.fofvar.barzot", icon = it),
                    )
                }!!,
                showCalendar = true,
                events = listOf(
                    Event("new titlelt ", LocalDateTime.now()),
                    Event("new titlelt 23  ", LocalDateTime.now()),
                    Event("new titlelst 23  ", LocalDateTime.now()),
                    Event("new titlelt 324", LocalDateTime.now()),
                )
            )
        )
    }
}

