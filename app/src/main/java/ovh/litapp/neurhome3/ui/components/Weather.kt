package ovh.litapp.neurhome3.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.litapp.neurhome3.data.weather.CurrentWeather
import ovh.litapp.neurhome3.data.weather.Daily
import ovh.litapp.neurhome3.data.weather.WeatherResponse
import ovh.litapp.neurhome3.ui.home.WeatherUIState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun Weather(
    modifier: Modifier = Modifier,
    weatherUIState: WeatherUIState,
) {
    Loading(modifier, loading = weatherUIState.loading) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            weatherUIState.weather?.let {
                Text(
                    text = "${it.current.temperature}°C",
                    fontSize = 32.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = getWeatherIcon(it.current.weatherCode),
                    fontSize = 48.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row {
                    for (i in 1..3) {
                        DailyForecast(
                            date = it.daily.time[i],
                            weatherCode = it.daily.weatherCode[i],
                            maxTemperature = it.daily.temperatureMax[i],
                            minTemperature = it.daily.temperatureMin[i]
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
    minTemperature: Double
) {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val dayOfWeek = LocalDate.parse(date, formatter).dayOfWeek.getDisplayName(
        java.time.format.TextStyle.SHORT,
        Locale.getDefault()
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Text(text = dayOfWeek)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = getWeatherIcon(weatherCode), fontSize = 24.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "${maxTemperature}°C / ${minTemperature}°C")
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

@Preview
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
