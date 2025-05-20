package ovh.litapp.neurhome3.ui.home

import android.app.AlarmManager
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import ovh.litapp.neurhome3.data.models.Event
import ovh.litapp.neurhome3.ui.AppViewModelProvider
import ovh.litapp.neurhome3.ui.INeurhomeViewModel
import ovh.litapp.neurhome3.ui.components.Calendar
import ovh.litapp.neurhome3.ui.components.Keyboard
import ovh.litapp.neurhome3.ui.components.Loading
import ovh.litapp.neurhome3.ui.theme.Neurhome3Theme
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val newHomeUIState by viewModel.homeUIState.collectAsState()

    Home(
        navController = navController,
        viewModel = viewModel,
        homeUIState = newHomeUIState
    )
}


@Composable
fun Home(
    navController: NavController,
    viewModel: IHomeViewModel,
    homeUIState: HomeUIState
) {
    BackHandler(true) { viewModel.clearQuery() }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        WatchArea(homeUIState.watchAreaUIState, viewModel)

        Calendar(Modifier.weight(1.2f, true), homeUIState.calendarUIState, viewModel::openCalendar)

        Column(
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(6f, true)
        ) {
            Loading(Modifier.weight(6f), homeUIState.topUIState.loading) {
                Box(
                    modifier = Modifier
                        .weight(6f, true)
                        .fillMaxWidth()
                ) {
                    HomeApplicationsList(
                        list = if (homeUIState.filteredUiState.query.isEmpty()) homeUIState.topUIState.apps else homeUIState.filteredUiState.apps,
                        appActions = viewModel.appActions,
                        filtering = homeUIState.filteredUiState.query.isNotEmpty()
                    )
                }
            }

            Loading(Modifier.weight(1f, true), homeUIState.filteredUiState.loading) {
                Box(modifier = Modifier.weight(3f, true)) {
                    Keyboard(
                        appsViewModel = viewModel,
                        appsUiState = homeUIState.filteredUiState
                    )
                }
            }

            Loading(Modifier.weight(1f, true), homeUIState.favouriteUIState.loading) {
                Box(modifier = Modifier.weight(1f, true)) {
                    BottomBar(homeUIState.favouriteUIState.apps, viewModel, navController)
                }
            }
        }
    }
}

data class HomePreviewParameter(val loading: Boolean, val alarm: Long?)

@Composable
fun HomePreview(
    params: HomePreviewParameter = HomePreviewParameter(
        true,
        null
    )
) {
    Neurhome3Theme {
        val drawable =
            AppCompatResources.getDrawable(LocalContext.current, R.drawable.icon)
        Home(
            navController = rememberNavController(),
            viewModel = object : IHomeViewModel {
                override fun push(s: String) {}
                override fun clearQuery() {}
                override fun pop() {}
                override fun openAlarms() {}

                override val vibrate = {}
                override fun openCalendar(event: Event) {}
                override val getBattery: () -> Intent?
                    get() = { null }

                override val appActions = INeurhomeViewModel.AppActions()

            },

            homeUIState = HomeUIState(
                calendarUIState = CalendarUIState(
                    loading = params.loading,
                    showCalendar = true,
                    events = listOf(
                        Event("new titlelt ", LocalDateTime.now().plusDays(3)),
                        Event("new titlelt 23  ", LocalDateTime.now().plusDays(4)),
                        Event("new titlelst 23  ", LocalDateTime.now()),
                        Event("new titl1elt 324", LocalDateTime.now()),
                        Event("new titl1elt 324", LocalDateTime.now()),
                        Event("new titl1elt 324", LocalDateTime.now()),
                        Event("new titl1elt 324", LocalDateTime.now()),
                        Event("new titl1elt 324", LocalDateTime.now()),
                        Event("new titl1elt 324", LocalDateTime.now()),
                    ),
                ),
                filteredUiState = FilteredUIState(
                    apps = drawable?.let {
                        listOf(
                            Application(
                                "Fooasd foofasd asdoasod",
                                "net.fofvar",
                                icon = it
                            ),
                            Application("Fooasd", "net.fofvar.klarna", icon = it),
                            Application("Fooasd", "net.fofvar.barzot", icon = it),
                            Application("Fooasd", "net.fofvar.barzot", icon = it),
                            Application("Fooasd", "net.fofvar.barzot", icon = it),
                            Application("Fooasd", "net.fofvar.barzot", icon = it),
                        )
                    }!!,
                    loading = params.loading,
                    query = arrayListOf("[abc][def]")
                ),
                favouriteUIState = FavouriteUIState(loading = params.loading),
                topUIState = TopUIState(
                    drawable.let {
                        listOf(
                            Application(
                                "Fooasd foofasd asdoasod",
                                "net.fofvar",
                                icon = it
                            ),
                            Application("Fooasd", "net.fofvar.klarna", icon = it),
                            Application("Fooasd", "net.fofvar.barzot", icon = it),
                            Application("Fooasd", "net.fofvar.barzot", icon = it),
                            Application("Fooasd", "net.fofvar.barzot", icon = it),
                            Application("Fooasd", "net.fofvar.barzot", icon = it),
                        )
                    }, loading = false
                ),
                watchAreaUIState = WatchAreaUIState(params.alarm?.let {
                    AlarmManager.AlarmClockInfo(it, null)
                }, false, ZonedDateTime.now(ZoneId.of("Europe/Dublin")))
            ),
        )
    }
}

@Preview
@Composable
fun HomePreview1() = HomePreview(HomePreviewParameter(true, null))

@Preview
@Composable
fun HomePreview2() = HomePreview(
    HomePreviewParameter(false, System.currentTimeMillis())
)

@Preview
@Composable
fun HomePreview3() = HomePreview(
    HomePreviewParameter(false, null)
)

