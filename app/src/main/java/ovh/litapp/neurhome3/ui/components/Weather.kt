package ovh.litapp.neurhome3.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.litapp.neurhome3.data.weather.CurrentWeather
import ovh.litapp.neurhome3.data.weather.WeatherResponse
import ovh.litapp.neurhome3.ui.home.WeatherUIState

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
            } ?: Text(text = "N/A")
        }
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
                )
            ),
            loading = false
        )
    )
}
