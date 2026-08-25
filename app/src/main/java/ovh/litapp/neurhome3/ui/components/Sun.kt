package ovh.litapp.neurhome3.ui.components

import android.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import ovh.litapp.neurhome3.data.solar.SolarEvent
import ovh.litapp.neurhome3.ui.home.SunUIState
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun Sun(
    modifier: Modifier = Modifier,
    sunUIState: SunUIState,
    onRefresh: () -> Unit = {}
) {
    val locationPermissionState = rememberPermissionState(
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    )

    LaunchedEffect(locationPermissionState.status.isGranted) {
        if (locationPermissionState.status.isGranted) {
            onRefresh()
        }
    }

    if (!locationPermissionState.status.isGranted) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = modifier.padding(16.dp)
        ) {
            Text(text = "Sun info requires location permission")
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { locationPermissionState.launchPermissionRequest() }) {
                Text(text = "Grant Permission")
            }
        }
    } else {
        Loading(modifier, loading = sunUIState.loading) {
            val event1 = sunUIState.firstEvent
            val event2 = sunUIState.secondEvent

            if (event1 != null && event2 != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    sunUIState.city?.let { city ->
                        Text(
                            text = city,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Top
                    ) {
                        SolarEventColumn(event = event1, modifier = Modifier.weight(1f))
                        SolarEventColumn(event = event2, modifier = Modifier.weight(1f))
                    }
                }
            } else {
                Text(text = "N/A")
            }
        }
    }
}

@Composable
private fun SolarEventColumn(
    event: SolarEvent,
    modifier: Modifier = Modifier
) {
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    fun formatTime(time: LocalTime?): String = time?.format(timeFormatter) ?: "--:--"
    val icon = if (event.title.contains("Sunrise", ignoreCase = true)) "🌅" else "🌇"

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = "$icon ${event.title} ${formatTime(event.time)}",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "🟦 Blue: ${formatTime(event.blueHourStart)} - ${formatTime(event.blueHourEnd)}",
            fontSize = 10.sp
        )
        Text(
            text = "🟨 Golden: ${formatTime(event.goldenHourStart)} - ${formatTime(event.goldenHourEnd)}",
            fontSize = 10.sp
        )
    }
}

@Preview(backgroundColor = Color.WHITE.toLong(), showBackground = true, widthDp = 310)
@Composable
fun SunPreview() {
    Sun(
        sunUIState = SunUIState(
            firstEvent = SolarEvent(
                title = "Today Sunset",
                time = LocalTime.of(18, 30),
                blueHourStart = LocalTime.of(18, 45),
                blueHourEnd = LocalTime.of(19, 0),
                goldenHourStart = LocalTime.of(17, 45),
                goldenHourEnd = LocalTime.of(18, 45)
            ),
            secondEvent = SolarEvent(
                title = "Tomorrow Sunrise",
                time = LocalTime.of(6, 46),
                blueHourStart = LocalTime.of(6, 16),
                blueHourEnd = LocalTime.of(6, 31),
                goldenHourStart = LocalTime.of(6, 31),
                goldenHourEnd = LocalTime.of(7, 16)
            ),
            city = "Paris",
            loading = false
        )
    )
}
