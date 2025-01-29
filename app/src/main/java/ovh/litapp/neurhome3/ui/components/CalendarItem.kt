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
import androidx.compose.material3.MaterialTheme
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
import ovh.litapp.neurhome3.data.Event
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
@Preview
fun CalendarItem(
    @PreviewParameter(provider = SampleEventProvider::class) event: Event,
    openEvent: (Event) -> Unit = { }
) {
    val multi = Duration.between(event.dtStart, event.end).toHours() > 24

    val (bgColor, color) = if (multi) Pair(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.inversePrimary
    ) else Pair(Color.Transparent, MaterialTheme.colorScheme.onSecondaryContainer)

    Row(horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
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
                    .width(3.dp)
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
                Text(
                    text = event.dtStart.format(DateTimeFormatter.ofPattern("dd/MM")),
                    color = color
                )
                Text(
                    if (event.allDay) "-" else event.dtStart.format(
                        DateTimeFormatter.ofPattern("HH:mm")
                    ), color = color
                )
            }
        }
        Text(
            event.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = color
        )
    }
}

class SampleEventProvider : PreviewParameterProvider<Event> {
    override val values = sequenceOf(
        Event(
            "asdas",
            dtStart = LocalDateTime.now(),
            end = LocalDateTime.now(),
            allDay = true,
            color = Color.Magenta
        ),
        Event("Other event", dtStart = LocalDateTime.now(), end = LocalDateTime.now()),
        Event(
            "Other event p eventeventeventeventeventeventeventevent",
            dtStart = LocalDateTime.now(),
            LocalDateTime.now()
        ),
        Event(
            "multi day event",
            dtStart = LocalDateTime.now().plusDays(1),
            end = LocalDateTime.now().plusHours(36),
            allDay = true
        )
    )
}