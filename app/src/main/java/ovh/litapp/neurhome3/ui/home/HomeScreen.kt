package ovh.litapp.neurhome3.ui.home

import android.app.AlarmManager
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val homeUiState by viewModel.homeUiState.collectAsState()
    val calendarUIState by viewModel.calendarUIState.collectAsState()
    val favouriteUIState by viewModel.favouriteUIState.collectAsState()

    Home(
        navController = navController,
        viewModel = viewModel,
        homeUiState = homeUiState,
        calendarUIState = calendarUIState,
        favouriteUIState = favouriteUIState
    )
}


@Composable
fun Home(
    navController: NavController,
    viewModel: IHomeViewModel,
    homeUiState: HomeUiState,
    calendarUIState: CalendarUIState,
    favouriteUIState: FavouriteUIState
) {
    BackHandler(true) { viewModel.clearQuery() }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        val alarm = homeUiState.alarm?.let {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it.triggerTime), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm"))
        }
        Row(modifier = Modifier.weight(1.0f)) {
            val blockStyle = { it: Float ->
                Modifier
                    .weight(it)
                    .fillMaxSize()
            }

            Box(modifier = blockStyle(0.25f)) { }
            Row(
                modifier = blockStyle(0.90f),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Watch(viewModel::openAlarms, viewModel.getBattery)
            }
            Box(modifier = blockStyle(0.25f)) {
                Column {
                    if (alarm != null) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Alarm,
                                contentDescription = "Next alarm time"
                            )
                            Text(text = alarm)
                        }
                    }
                }
            }
        }

        Calendar(Modifier.weight(1.2f, true), calendarUIState, viewModel::openCalendar)

        Column(
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(6f, true)
        ) {
            if (homeUiState.loading) {
                Loading(Modifier.weight(6f))
            } else {
                Box(
                    modifier = Modifier
                        .weight(6f, true)
                        .fillMaxWidth()
                ) {
                    HomeApplicationsList(
                        list = homeUiState.homeApps,
                        appActions = viewModel.appActions,
                        filtering = homeUiState.query.isNotEmpty()
                    )
                }
            }
            Box(modifier = Modifier.weight(3f, true)) {
                Keyboard(appsViewModel = viewModel, appsUiState = homeUiState)
            }
            if (favouriteUIState.loading){
                Loading(Modifier.weight(1f, true))
            }else{
                Box(modifier = Modifier.weight(1f, true)) {
                    BottomBar(favouriteUIState.apps, viewModel, navController)
                }
            }

        }
    }
}

@Composable
fun Calendar(
    modifier: Modifier,
    calendarUIState: CalendarUIState,
    onEventClick: (Event) -> Unit
) {
    if (calendarUIState.loading) {
        Loading(modifier)
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            if (calendarUIState.showCalendar) {
                Calendar(
                    list = calendarUIState.events, onEventClick
                )
            }
        }
    }
}

@Composable
private fun Loading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
    ) {
        CircularProgressIndicator(
            modifier = modifier,
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
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
            AppCompatResources.getDrawable(LocalContext.current, R.drawable.ic_launcher_background)
        Home(
            navController = rememberNavController(), viewModel = object : IHomeViewModel {
                override fun push(s: String) {}
                override fun clearQuery() {}
                override fun pop() {}
                override fun openAlarms() {}

                override val vibrate = {}
                override fun openCalendar(event: Event) {}
                override val getBattery: () -> Intent?
                    get() = { null }

                override val appActions = INeurhomeViewModel.AppActions()

            }, homeUiState = HomeUiState(
                homeApps = drawable?.let {
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

                alarm = params.alarm?.let {
                    AlarmManager.AlarmClockInfo(it, null)
                },
                loading = params.loading,
                query = arrayListOf("[abc][def]")
            ), calendarUIState = CalendarUIState(
                loading = params.loading,
                showCalendar = true,
                events = listOf(
                    Event("new titlelt ", LocalDateTime.now()),
                    Event("new titlelt 23  ", LocalDateTime.now()),
                    Event("new titlelst 23  ", LocalDateTime.now()),
                    Event("new titl1elt 324", LocalDateTime.now()),
                    Event("new titl1elt 324", LocalDateTime.now()),
                    Event("new titl1elt 324", LocalDateTime.now()),
                    Event("new titl1elt 324", LocalDateTime.now()),
                    Event("new titl1elt 324", LocalDateTime.now()),
                    Event("new titl1elt 324", LocalDateTime.now()),
                ),
            ), favouriteUIState = FavouriteUIState(loading = params.loading)
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

