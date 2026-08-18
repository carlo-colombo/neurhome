package ovh.litapp.neurhome3.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import ovh.litapp.neurhome3.data.models.Event
import ovh.litapp.neurhome3.ui.home.CalendarUIState
import ovh.litapp.neurhome3.ui.home.WeatherUIState

import ovh.litapp.neurhome3.ui.home.SunUIState

@Composable
fun SwipeableContainer(
    modifier: Modifier = Modifier,
    calendarUIState: CalendarUIState,
    weatherUIState: WeatherUIState,
    sunUIState: SunUIState = SunUIState(),
    onEventClick: (Event) -> Unit,
    onWeatherShown: () -> Unit = {},
    onSunShown: () -> Unit = {}
) {
    val pagerState = rememberPagerState { 3 }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            when (page) {
                1 -> onWeatherShown()
                2 -> onSunShown()
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier
    ) { page ->
        Box(modifier = modifier) {
            when (page) {
                0 -> Calendar(
                    calendarUIState = calendarUIState,
                    onEventClick = onEventClick
                )
                1 -> Weather(
                    weatherUIState = weatherUIState,
                    onRefresh = onWeatherShown
                )
                2 -> Sun(
                    sunUIState = sunUIState,
                    onRefresh = onSunShown
                )
            }
        }
    }
}
