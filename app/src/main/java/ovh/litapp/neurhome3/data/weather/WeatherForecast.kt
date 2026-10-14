package ovh.litapp.neurhome3.data.weather

/**
 * One renderable forecast row. [Daily] carries four independent lists that Open-Meteo fills in
 * parallel; a partial or longer response must not be indexed past the end of any of them.
 */
internal data class ForecastDay(
    val date: String,
    val weatherCode: Int,
    val maxTemperature: Double,
    val minTemperature: Double,
)

/** `WeatherServiceImpl` requests `forecast_days=4`, matching the four columns of the weather row. */
private const val FORECAST_DAY_COUNT = 4

/**
 * Pairs the parallel [Daily] lists into at most [FORECAST_DAY_COUNT] rows, stopping at the
 * shortest list so a response with fewer days renders fewer columns instead of throwing.
 */
internal fun forecastDays(daily: Daily): List<ForecastDay> {
    val count = minOf(
        FORECAST_DAY_COUNT,
        daily.time.size,
        daily.weatherCode.size,
        daily.temperatureMax.size,
        daily.temperatureMin.size
    )
    return List(count) { index ->
        ForecastDay(
            date = daily.time[index],
            weatherCode = daily.weatherCode[index],
            maxTemperature = daily.temperatureMax[index],
            minTemperature = daily.temperatureMin[index]
        )
    }
}
