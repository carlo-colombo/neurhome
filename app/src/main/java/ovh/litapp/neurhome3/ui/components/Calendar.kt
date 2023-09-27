package ovh.litapp.neurhome3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import ovh.litapp.neurhome3.data.Event
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalPermissionsApi::class)
@Composable
@Preview
fun Calendar(
    @PreviewParameter(SampleEventsProvider::class) list: List<Event>,
    openEvent: (Event) -> Unit = {}
) {

    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(items = list, key = {
            it.title + it.id
        }) { event -> CalendarItem(event = event, openEvent = openEvent) }

    }

}


@Composable
@Preview
fun CalendarItem(
    @PreviewParameter(provider = SampleEventProvider::class) event: Event,
    openEvent: (Event) -> Unit = { }
) {
    Row(horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                openEvent(event)
            }) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.30f)
                .height(IntrinsicSize.Min)
                .width(IntrinsicSize.Max)
                .padding(PaddingValues(end = 10.dp))
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .clip(RectangleShape)
                    .background(event.color)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(PaddingValues(start = 2.dp)),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = event.dtStart.format(DateTimeFormatter.ofPattern("dd/MM")))
                Text(
                    if (event.allDay) "-" else event.dtStart.format(
                        DateTimeFormatter.ofPattern("HH:mm")

                    )
                )
            }
        }
        Text(
            event.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

class SampleEventProvider : PreviewParameterProvider<Event> {
    override val values = sequenceOf(
        Event("asdas", dtStart = LocalDateTime.now(), allDay = true, color = Color.Magenta),
        Event("Other event", dtStart = LocalDateTime.now()),
        Event(
            "Other event p eventeventeventeventeventeventeventevent",
            dtStart = LocalDateTime.now(),
        )
    )
}


class SampleEventsProvider : PreviewParameterProvider<List<Event>> {
    override val values = sequenceOf(
        listOf(
            Event(
                "Other event",
                dtStart = LocalDateTime.parse("2007-11-11T11:11:11"),
            ),
            Event("asdas", dtStart = LocalDateTime.MAX, allDay = true, color = Color.Cyan),
            Event(
                "sOther event p eventeventeventeventeventeventeventevent", allDay = true,
                dtStart = LocalDateTime.of(2020, 10, 10, 20, 10),
            ),
            Event(
                "Other event p eventeventeventeventeventeventeventevent",
                dtStart = LocalDateTime.now(),
            ),
            Event(
                "Other events", dtStart = LocalDateTime.now(),
            )
        )
    )
}

