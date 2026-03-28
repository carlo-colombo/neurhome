package ovh.litapp.neurhome3.ui.components

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
                    calendarUIState.events.groupBy { it.dtStart.toLocalDate() }
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

@Preview
@Composable
fun CalendarPreview() {
    Calendar(calendarUIState = SampleEventsProvider().values.first())
}

class SampleEventsProvider : PreviewParameterProvider<CalendarUIState> {
    override val values = sequenceOf(
        CalendarUIState(
            listOf(
                Event(
                    "Other event",
                    dtStart = LocalDateTime.parse("2007-11-11T11:11:11"), null
                ),
                Event(
                    "asdas",
                    dtStart = LocalDateTime.MAX,
                    null,
                    allDay = true,
                    color = Color.Cyan
                ),
                Event(
                    "multi sOther event p eventeventeventeventeventeventeventevent", allDay = true,
                    dtStart = LocalDateTime.of(2020, 10, 10, 20, 10),
                    end = LocalDateTime.now()
                ),
                Event(
                    "Other event p eventeventeventeventeventeventeventevent",
                    dtStart = LocalDateTime.now(), null
                ),
                Event(
                    "Other events", dtStart = LocalDateTime.now(), null
                )
            ), showCalendar = true, loading = false
        )
    )
}
