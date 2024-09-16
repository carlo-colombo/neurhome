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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
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
                modifier = blockStyle(0.50f),
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

        if (homeUiState.loading) {
            Box(
                modifier = Modifier.weight(11f)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.width(200.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        } else {
            Box(modifier = Modifier.weight(1.2f, true), contentAlignment = Alignment.Center) {
                if (homeUiState.showCalendar) {
                    Calendar(
                        list = homeUiState.events, openEvent = viewModel::openCalendar
                    )
                }
            }

            Column(verticalArrangement = Arrangement.Bottom, modifier = Modifier.weight(6f, true)) {
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
                Box(modifier = Modifier.weight(3f, true)) {
                    Keyboard(appsViewModel = viewModel, appsUiState = homeUiState)
                }
                Box(modifier = Modifier.weight(1f, true)) {
                    BottomBar(homeUiState, viewModel, navController)
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
                    get() = {null}

                override val appActions = INeurhomeViewModel.AppActions()

            }, homeUiState = HomeUiState(
                homeApps = drawable?.let {
                    listOf(
                        Application(
                            "Fooasd foofasd asdoasod",
                            "net.fofvar",
                            icon = it,

                            ),
                        Application("Fooasd", "net.fofvar.klarna", icon = it),
                        Application("Fooasd", "net.fofvar.barzot", icon = it),
                        Application("Fooasd", "net.fofvar.barzot", icon = it),
                        Application("Fooasd", "net.fofvar.barzot", icon = it),
                        Application("Fooasd", "net.fofvar.barzot", icon = it),
                    )
                }!!,
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
                alarm = params.alarm?.let {
                    AlarmManager.AlarmClockInfo(it, null)
                },
                loading = params.loading,
                query = arrayListOf("[abc][def]")
            )
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

