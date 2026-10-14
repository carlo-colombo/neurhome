package ovh.litapp.neurhome3.ui.components

import android.Manifest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ovh.litapp.neurhome3.ComposeTestActivity
import ovh.litapp.neurhome3.data.weather.CurrentWeather
import ovh.litapp.neurhome3.data.weather.Daily
import ovh.litapp.neurhome3.data.weather.WeatherResponse
import ovh.litapp.neurhome3.ui.home.WeatherUIState

/**
 * Renders [Weather] itself: the crash this pins happened at the composable's call site, so a
 * response with fewer days than the four-column row must render one column per available day
 * instead of indexing past the end of the parallel `Daily` lists.
 */
@RunWith(AndroidJUnit4::class)
class WeatherForecastRowTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComposeTestActivity>()

    @Before
    fun grantLocationPermission() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    @Test
    fun aResponseWithFewerDaysThanTheRowRendersOneColumnPerDay() {
        renderForecast(dayCount = 1)

        composeTestRule.onNodeWithText("Today").assertIsDisplayed()
        // The current column shows "12°C" and "15°/5°"; no second day column exists.
        composeTestRule.onAllNodesWithText("°", substring = true).assertCountEquals(2)
    }

    @Test
    fun aFullFourDayResponseRendersTodayAndThreeRemainingColumns() {
        renderForecast(dayCount = 4)

        composeTestRule.onNodeWithText("Today").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("°", substring = true).assertCountEquals(5)
    }

    private fun renderForecast(dayCount: Int) {
        composeTestRule.setContent {
            Weather(
                weatherUIState = WeatherUIState(
                    weather = WeatherResponse(
                        current = CurrentWeather(temperature = 12.3, weatherCode = 0),
                        daily = Daily(
                            time = List(dayCount) { "2024-01-0${it + 1}" },
                            weatherCode = List(dayCount) { it },
                            temperatureMax = List(dayCount) { 15.0 + it },
                            temperatureMin = List(dayCount) { 5.0 + it }
                        )
                    ),
                    city = "Paris",
                    loading = false
                )
            )
        }
    }
}
