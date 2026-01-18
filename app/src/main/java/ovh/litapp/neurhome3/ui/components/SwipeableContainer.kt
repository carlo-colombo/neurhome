package ovh.litapp.neurhome3.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import ovh.litapp.neurhome3.data.models.Event
import ovh.litapp.neurhome3.ui.home.CalendarUIState
import ovh.litapp.neurhome3.ui.home.WeatherUIState

@Composable
fun SwipeableContainer(
    modifier: Modifier = Modifier,
    calendarUIState: CalendarUIState,
    weatherUIState: WeatherUIState,
    onEventClick: (Event) -> Unit
) {
    val pagerState = rememberPagerState()

    HorizontalPager(
        count = 2,
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
                    weatherUIState = weatherUIState
                )
            }
        }
    }
}
