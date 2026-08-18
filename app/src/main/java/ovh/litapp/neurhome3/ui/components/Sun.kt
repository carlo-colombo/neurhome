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
import ovh.litapp.neurhome3.data.solar.SolarTimes
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
            sunUIState.solarTimes?.let { times ->
                val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
                fun formatTime(time: LocalTime?): String = time?.format(timeFormatter) ?: "--:--"

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
                        // Sunrise side
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "🌅 Sunrise ${formatTime(times.sunrise)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "🟦 Blue: ${formatTime(times.morningBlueHourStart)} - ${formatTime(times.morningBlueHourEnd)}",
                                fontSize = 10.sp
                            )
                            Text(
                                text = "🟨 Golden: ${formatTime(times.morningGoldenHourStart)} - ${formatTime(times.morningGoldenHourEnd)}",
                                fontSize = 10.sp
                            )
                        }

                        // Sunset side
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "🌇 Sunset ${formatTime(times.sunset)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "🟦 Blue: ${formatTime(times.eveningBlueHourStart)} - ${formatTime(times.eveningBlueHourEnd)}",
                                fontSize = 10.sp
                            )
                            Text(
                                text = "🟨 Golden: ${formatTime(times.eveningGoldenHourStart)} - ${formatTime(times.eveningGoldenHourEnd)}",
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            } ?: Text(text = "N/A")
        }
    }
}

@Preview(backgroundColor = Color.WHITE.toLong(), showBackground = true, widthDp = 310)
@Composable
fun SunPreview() {
    Sun(
        sunUIState = SunUIState(
            solarTimes = SolarTimes(
                sunrise = LocalTime.of(6, 45),
                sunset = LocalTime.of(18, 30),
                morningBlueHourStart = LocalTime.of(6, 15),
                morningBlueHourEnd = LocalTime.of(6, 30),
                eveningBlueHourStart = LocalTime.of(18, 45),
                eveningBlueHourEnd = LocalTime.of(19, 0),
                morningGoldenHourStart = LocalTime.of(6, 30),
                morningGoldenHourEnd = LocalTime.of(7, 15),
                eveningGoldenHourStart = LocalTime.of(17, 45),
                eveningGoldenHourEnd = LocalTime.of(18, 45)
            ),
            city = "Paris",
            loading = false
        )
    )
}
