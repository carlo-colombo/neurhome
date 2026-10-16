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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ovh.litapp.neurhome3.R
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.models.Event
import ovh.litapp.neurhome3.data.models.Tag
import ovh.litapp.neurhome3.application.AppViewModelFactory
import ovh.litapp.neurhome3.ui.INeurhomeViewModel
import ovh.litapp.neurhome3.ui.components.Keyboard
import ovh.litapp.neurhome3.ui.components.Loading
import ovh.litapp.neurhome3.ui.components.SwipeableContainer
import ovh.litapp.neurhome3.ui.theme.Neurhome3Theme
import java.time.LocalDateTime

@Composable
fun HomeScreen(
    onOpenApplications: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val newHomeUIState by viewModel.homeUIState.collectAsStateWithLifecycle()

    Home(
        onOpenApplications = onOpenApplications,
        viewModel = viewModel,
        homeUIState = newHomeUIState,
    )
}


@Composable
fun Home(
    onOpenApplications: () -> Unit = {},
    viewModel: IHomeViewModel,
    homeUIState: HomeUIState,
) {
    val filtering = homeUIState.filteredUIState.query.isNotEmpty()
    val availableTags by viewModel.tags.collectAsStateWithLifecycle()
    BackHandler(enabled = filtering) {
        viewModel.clearQuery()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        WatchArea(
            watchAreaUIState = homeUIState.watchAreaUIState,
            openAlarms = viewModel::openAlarms,
            getBattery = viewModel.getBattery,
        )

        SwipeableContainer(
            modifier = Modifier.weight(1.2f, true),
            calendarUIState = homeUIState.calendarUIState,
            weatherUIState = homeUIState.weatherUIState,
            sunUIState = homeUIState.sunUIState,
            onEventClick = viewModel::openCalendar,
            onCalendarShown = viewModel::refreshCalendar,
            onWeatherShown = viewModel::fetchWeather,
            onSunShown = viewModel::fetchSun
        )

        if (filtering) {
            FilteredAppsHomeLayout(
                modifier = Modifier.weight(5.5f, true).fillMaxWidth(),
                apps = homeUIState.filteredUIState.apps,
                loading = homeUIState.filteredUIState.loading,
                onLaunch = viewModel.appActions.launch,
                managementActions = viewModel.appActions.management,
                tagActions = viewModel.appActions.tags,
                availableTags = availableTags.map { it.name },
            )
        } else {
            TopAppsHomeLayout(
                modifier = Modifier.weight(5.5f, true).fillMaxWidth(),
                apps = homeUIState.topUIState.apps,
                loading = homeUIState.topUIState.loading,
                onLaunch = viewModel.appActions.launch,
                managementActions = viewModel.appActions.management,
                tagActions = viewModel.appActions.tags,
                availableTags = availableTags.map { it.name },
            )
        }

        Keyboard(
            appsViewModel = viewModel,
            appsUiState = homeUIState.filteredUIState,
        )
        BottomBar(homeUIState.favouriteUIState.apps, viewModel, onOpenApplications)
    }
}

@Composable
private fun TopAppsHomeLayout(
    modifier: Modifier,
    apps: List<Application>,
    loading: Boolean,
    onLaunch: (Application?, track: Boolean) -> Unit,
    managementActions: INeurhomeViewModel.AppManagementActions,
    tagActions: INeurhomeViewModel.AppTagActions,
    availableTags: List<String>,
) {
    Box(modifier = modifier) {
        Loading(Modifier.fillMaxSize(), loading) {
            HomeApplicationsList(
                list = apps,
                onLaunch = onLaunch,
                filtering = false,
                managementActions = managementActions,
                tagActions = tagActions,
                availableTags = availableTags,
            )
        }
    }
}

@Composable
private fun FilteredAppsHomeLayout(
    modifier: Modifier,
    apps: List<Application>,
    loading: Boolean,
    onLaunch: (Application?, track: Boolean) -> Unit,
    managementActions: INeurhomeViewModel.AppManagementActions,
    tagActions: INeurhomeViewModel.AppTagActions,
    availableTags: List<String>,
) {
    Box(modifier = modifier) {
        Loading(Modifier.fillMaxSize(), loading) {
            HomeApplicationsList(
                list = apps,
                onLaunch = onLaunch,
                filtering = true,
                managementActions = managementActions,
                tagActions = tagActions,
                availableTags = availableTags,
            )
        }
    }
}

data class HomePreviewParameter(val loading: Boolean, val alarm: Long?)

/**
 * Preview-only [IHomeViewModel]: no-op actions plus the empty tag list, so the previews no longer
 * carry a TODO() body for every member of the interface.
 */
private object PreviewHomeViewModel : IHomeViewModel {
    override fun push(s: String) = Unit
    override fun clearQuery() = Unit
    override fun pop() = Unit
    override fun openAlarms() = Unit
    override fun fetchWeather() = Unit
    override val vibrate: () -> Unit = {}
    override fun openCalendar(event: Event) = Unit
    override val getBattery: () -> Intent? = { null }
    override val appActions = INeurhomeViewModel.AppActions()
    override val tags: StateFlow<List<Tag>> = MutableStateFlow(emptyList())
}

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
            viewModel = PreviewHomeViewModel,

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
                filteredUIState = FilteredUIState(
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
                watchAreaUIState = WatchAreaUIState(
                    params.alarm?.let {
                        AlarmManager.AlarmClockInfo(it, null)
                    },
                    false,
                    showAlternativeTime = true,
                    alternativeTimeZone = "Europe/Dublin",
                )
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
