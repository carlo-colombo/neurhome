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
import ovh.litapp.neurhome3.data.weather.CurrentWeather
import ovh.litapp.neurhome3.data.weather.Daily
import ovh.litapp.neurhome3.data.weather.WeatherResponse
import ovh.litapp.neurhome3.ui.home.WeatherUIState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun Weather(
    modifier: Modifier = Modifier,
    weatherUIState: WeatherUIState,
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
            Text(text = "Weather requires location permission")
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { locationPermissionState.launchPermissionRequest() }) {
                Text(text = "Grant Permission")
            }
        }
    } else {
        Loading(modifier, loading = weatherUIState.loading) {
            weatherUIState.weather?.let {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0..3) {
                        DailyForecast(
                            date = it.daily.time[i],
                            weatherCode = it.daily.weatherCode[i],
                            maxTemperature = it.daily.temperatureMax[i],
                            minTemperature = it.daily.temperatureMin[i],
                            currentTemperature = if (i == 0) it.current.temperature else null
                        )
                    }
                }
            } ?: Text(text = "N/A")
        }
    }
}

@Composable
fun DailyForecast(
    date: String,
    weatherCode: Int,
    maxTemperature: Double,
    minTemperature: Double,
    currentTemperature: Double? = null
) {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val dayOfWeek = LocalDate.parse(date, formatter).dayOfWeek.getDisplayName(
        java.time.format.TextStyle.SHORT,
        Locale.getDefault()
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Text(
            text = if (currentTemperature != null) "Today" else dayOfWeek,
            fontSize = 12.sp,
            fontWeight = if (currentTemperature != null) FontWeight.Bold else FontWeight.Normal
        )
        Text(text = getWeatherIcon(weatherCode), fontSize = 20.sp)
        if (currentTemperature != null) {
            Text(text = "${currentTemperature.toInt()}°C", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            text = "${maxTemperature.toInt()}°/${minTemperature.toInt()}°",
            fontSize = 10.sp
        )
    }
}

private fun getWeatherIcon(weatherCode: Int): String {
    return when (weatherCode) {
        0 -> "☀️"
        1, 2, 3 -> "⛅️"
        45, 48 -> "☁️"
        51, 53, 55 -> "🌧"
        56, 57 -> "🌧"
        61, 63, 65 -> "🌧"
        66, 67 -> "🌧"
        71, 73, 75 -> "❄️"
        77 -> "❄️"
        80, 81, 82 -> "🌧"
        85, 86 -> "❄️"
        95 -> "⛈"
        96, 99 -> "⛈"
        else -> "🤷"
    }
}

@Preview(backgroundColor = Color.WHITE.toLong(), showBackground = true, widthDp = 310)
@Composable
fun WeatherPreview() {
    Weather(
        weatherUIState = WeatherUIState(
            weather = WeatherResponse(
                current = CurrentWeather(
                    temperature = 12.3,
                    weatherCode = 0
                ),
                daily = Daily(
                    time = listOf(
                        "2023-01-01",
                        "2023-01-02",
                        "2023-01-03",
                        "2023-01-04"
                    ),
                    weatherCode = listOf(0, 1, 2, 3),
                    temperatureMax = listOf(15.0, 16.0, 17.0, 18.0),
                    temperatureMin = listOf(10.0, 11.0, 12.0, 13.0)
                )
            ),
            loading = false
        )
    )
}
