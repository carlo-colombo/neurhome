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

@Composable
fun SwipeableContainer(
    modifier: Modifier = Modifier,
    calendarUIState: CalendarUIState,
    weatherUIState: WeatherUIState,
    onEventClick: (Event) -> Unit,
    onWeatherShown: () -> Unit = {}
) {
    val pagerState = rememberPagerState { 2 }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (page == 1) {
                onWeatherShown()
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
            }
        }
    }
}
