package ovh.litapp.neurhome3.data.weather

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class WeatherResponse(
    @SerialName("current")
    val current: CurrentWeather,
    @SerialName("daily")
    val daily: Daily
)

@Serializable
data class CurrentWeather(
    @SerialName("temperature_2m")
    val temperature: Double,
    @SerialName("weather_code")
    val weatherCode: Int
)

@Serializable
data class Daily(
    @SerialName("time")
    val time: List<String>,
    @SerialName("weathercode")
    val weatherCode: List<Int>,
    @SerialName("temperature_2m_max")
    val temperatureMax: List<Double>,
    @SerialName("temperature_2m_min")
    val temperatureMin: List<Double>
)


interface WeatherService {
    suspend fun getWeather(latitude: Double, longitude: Double): WeatherResponse
}

class WeatherServiceImpl : WeatherService {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
    }

    override suspend fun getWeather(latitude: Double, longitude: Double): WeatherResponse {
        val url =
            "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,weather_code&daily=weathercode,temperature_2m_max,temperature_2m_min&forecast_days=4"
        return client.get(url).body()
    }
}

class WeatherRepository(private val weatherService: WeatherService) {
    suspend fun getWeather(latitude: Double, longitude: Double): Result<WeatherResponse> {
        return try {
            Result.success(weatherService.getWeather(latitude, longitude))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
