package ovh.litapp.neurhome3.data.weather

/**
 * The Open-Meteo forecast request, kept as a pure builder so the parameters can be asserted
 * without a network round trip. A misspelled parameter name is invisible at runtime: the API
 * answers with an empty `current`/`daily` payload, `Json { ignoreUnknownKeys = true }` decodes it
 * into the non-null lists of [WeatherResponse], and the forecast renders "N/A" forever.
 * [WeatherForecastUrlTest] therefore pins both the URL and the fact that every requested field is
 * one [WeatherResponse] decodes.
 */
object WeatherForecastUrl {
    const val ENDPOINT = "https://api.open-meteo.com/v1/forecast"

    /** `current` fields; each one is a serial name of [CurrentWeather]. */
    const val CURRENT_FIELDS = "temperature_2m,weather_code"

    /**
     * `daily` fields; each one is a serial name of [Daily]. `time` is deliberately absent: the
     * API returns it for `daily` requests whether or not it is requested.
     */
    const val DAILY_FIELDS = "weathercode,temperature_2m_max,temperature_2m_min"

    const val FORECAST_DAYS = 4

    fun forecast(latitude: Double, longitude: Double): String =
        "$ENDPOINT?latitude=$latitude&longitude=$longitude" +
            "&current=$CURRENT_FIELDS&daily=$DAILY_FIELDS&forecast_days=$FORECAST_DAYS"
}
