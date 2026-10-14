package ovh.litapp.neurhome3.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `DailyForecast` renders [getWeatherIcon] for every WMO code Open-Meteo can return, so a
 * mistyped branch would silently show the wrong icon.
 */
class WeatherIconTest {
    @Test
    fun everyWmoCodeInTheOpenMeteoTableMapsToItsIcon() {
        val table = mapOf(
            0 to "☀️",
            1 to "⛅️",
            2 to "⛅️",
            3 to "⛅️",
            45 to "☁️",
            48 to "☁️",
            51 to "🌧",
            53 to "🌧",
            55 to "🌧",
            56 to "🌧",
            57 to "🌧",
            61 to "🌧",
            63 to "🌧",
            65 to "🌧",
            66 to "🌧",
            67 to "🌧",
            71 to "❄️",
            73 to "❄️",
            75 to "❄️",
            77 to "❄️",
            80 to "🌧",
            81 to "🌧",
            82 to "🌧",
            85 to "❄️",
            86 to "❄️",
            95 to "⛈",
            96 to "⛈",
            99 to "⛈"
        )

        table.forEach { (code, icon) -> assertEquals("WMO code $code", icon, getWeatherIcon(code)) }
    }

    @Test
    fun unknownCodesFallBackToThePlaceholder() {
        assertEquals("🤷", getWeatherIcon(-1))
        assertEquals("🤷", getWeatherIcon(42))
        assertEquals("🤷", getWeatherIcon(100))
    }
}
