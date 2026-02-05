package ovh.litapp.neurhome3.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import ovh.litapp.neurhome3.data.models.Event
import ovh.litapp.neurhome3.ui.home.CalendarUIState
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun Calendar(
    modifier: Modifier = Modifier,
    @PreviewParameter(SampleEventsProvider::class) calendarUIState: CalendarUIState,
    onEventClick: (Event) -> Unit = {}
) {
    Loading(modifier, loading = calendarUIState.loading) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            if (calendarUIState.showCalendar) {
                val today = LocalDate.now()
                val events = calendarUIState.events.filter { event ->
                    !event.isMultiDay || !event.dtStart.toLocalDate().isBefore(today)
                }

                val sortedEvents = events.groupBy { it.dtStart.toLocalDate() }
                    .toSortedMap()
                    .flatMap { (_, events) ->
                        events.sortedWith(
                            compareByDescending<Event> { it.isMultiDay || it.allDay }
                                .thenBy { it.dtStart }
                        )
                    }
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
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