package ovh.litapp.neurhome3.ui.components

import android.provider.CalendarContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ovh.litapp.neurhome3.data.models.Event
import ovh.litapp.neurhome3.ui.home.CalendarUIState
import java.time.LocalDate
import java.time.LocalDateTime

private const val INACTIVITY_TIMEOUT_MS = 5 * 60 * 1000L

@Composable
fun Calendar(
    modifier: Modifier = Modifier,
    @PreviewParameter(SampleEventsProvider::class) calendarUIState: CalendarUIState,
    onEventClick: (Event) -> Unit = {}
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(lastInteractionTime) {
        delay(INACTIVITY_TIMEOUT_MS)
        listState.animateScrollToItem(0)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (System.currentTimeMillis() - lastInteractionTime >= INACTIVITY_TIMEOUT_MS) {
                    coroutineScope.launch {
                        listState.scrollToItem(0)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Loading(modifier, loading = calendarUIState.loading) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            if (calendarUIState.showCalendar) {
                val sortedEvents = remember(calendarUIState.events) {
                    val today = LocalDate.now()
                    calendarUIState.events
                        .filter { !it.dtStart.toLocalDate().isBefore(today) }
                        .groupBy { it.dtStart.toLocalDate() }
                        .toSortedMap()
                        .flatMap { (_, events) ->
                            events.sortedWith(
                                compareByDescending<Event> { it.isMultiDay || it.allDay }
                                    .thenBy { it.dtStart }
                            )
                        }
                }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(pass = PointerEventPass.Initial)
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                            }
                        }
                ) {
                    items(items = sortedEvents, key = {
                        it.title + it.id + it.dtStart + it.eventId
                    }) { event ->
                        CalendarItem(
                            event = event, openEvent = onEventClick
                        )
                    }
                }
            }
        }
    }
}

@Preview(backgroundColor = android.graphics.Color.WHITE.toLong(),
    showBackground = true, widthDp = 310)
@Composable
fun CalendarPreview() {
    Calendar(calendarUIState = SampleEventsProvider().values.first())
}

class SampleEventsProvider : PreviewParameterProvider<CalendarUIState> {
    override val values = sequenceOf(
        CalendarUIState(
            events = listOf(
                Event(
                    title = "Monday Morning Standup",
                    dtStart = LocalDateTime.now().withHour(9).withMinute(0),
                    end = LocalDateTime.now().withHour(9).withMinute(30),
                    color = Color.Blue
                ),
                Event(
                    title = "Lunch with Team",
                    dtStart = LocalDateTime.now().withHour(12).withMinute(30),
                    end = LocalDateTime.now().withHour(13).withMinute(30),
                    color = Color.Green
                ),
                Event(
                    title = "Tuesday Review",
                    dtStart = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0),
                    end = LocalDateTime.now().plusDays(1).withHour(11).withMinute(0),
                    color = Color.Red
                ),
                Event(
                    title = "All Day Workshop",
                    dtStart = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0),
                    allDay = true,
                    color = Color.Magenta
                ),
                Event(
                    title = "Wednesday Deep Work",
                    dtStart = LocalDateTime.now().plusDays(2).withHour(14).withMinute(0),
                    end = LocalDateTime.now().plusDays(2).withHour(17).withMinute(0),
                    color = Color.Cyan
                ),
                Event(
                    title = "Thursday Gym Session",
                    dtStart = LocalDateTime.now().plusDays(3).withHour(18).withMinute(0),
                    end = LocalDateTime.now().plusDays(3).withHour(19).withMinute(30),
                    color = Color.Yellow
                ),
                Event(
                    title = "Friday Drinks",
                    dtStart = LocalDateTime.now().plusDays(4).withHour(19).withMinute(0),
                    end = LocalDateTime.now().plusDays(4).withHour(21).withMinute(0),
                    color = Color.Red
                ),
                Event(
                    title = "Saturday Hike",
                    dtStart = LocalDateTime.now().plusDays(5).withHour(8).withMinute(0),
                    end = LocalDateTime.now().plusDays(5).withHour(16).withMinute(0),
                    allDay = true,
                    color = Color.Green
                ),
                Event(
                    title = "Sunday Brunch",
                    dtStart = LocalDateTime.now().plusDays(6).withHour(11).withMinute(0),
                    end = LocalDateTime.now().plusDays(6).withHour(13).withMinute(0),
                    color = Color.Magenta
                ),
                Event(
                    title = "Ongoing Project",
                    dtStart = LocalDateTime.now().minusDays(1),
                    end = LocalDateTime.now().plusDays(3),
                    allDay = true,
                    color = Color.Gray,
                    originalDtStart = LocalDateTime.now().minusDays(1)
                )
            ),
            showCalendar = true,
            loading = false
        )
    )
}
