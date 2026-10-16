package ovh.litapp.neurhome3.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
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
 *
 * Each day column is asserted through the text it renders - the "Today" label of the current
 * column and the distinct max/min pair of every column - rather than through the number of "°"
 * substrings in the tree. The pair of a day is fixed by the fixture, so the assertion does not
 * depend on the device locale that names the remaining weekdays.
 */
@RunWith(AndroidJUnit4::class)
class WeatherForecastRowTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun aResponseWithFewerDaysThanTheRowRendersOneColumnPerDay() {
        renderForecastRow(dayCount = 1)

        // The single available day renders the current column: its label and its own pair.
        composeTestRule.onNodeWithText("Today").assertIsDisplayed()
        composeTestRule.onNodeWithText(dayPair(day = 0)).assertIsDisplayed()
        // A day the response never carried has no column to render into.
        composeTestRule.onNodeWithText(dayPair(day = 1)).assertDoesNotExist()
    }

    @Test
    fun aResponseWithMoreDaysThanTheRowRendersOnlyFourColumns() {
        renderForecastRow(dayCount = 6)

        composeTestRule.onNodeWithText("Today").assertIsDisplayed()
        (0 until 4).forEach { day ->
            composeTestRule.onNodeWithText(dayPair(day = day)).assertIsDisplayed()
        }
        // The row is capped: the fifth and sixth available days have no column.
        composeTestRule.onNodeWithText(dayPair(day = 4)).assertDoesNotExist()
        composeTestRule.onNodeWithText(dayPair(day = 5)).assertDoesNotExist()
    }

    private fun renderForecastRow(dayCount: Int) {
        composeTestRule.setContent {
            // Weather only renders its row while the location permission is granted, and the
            // grant state of the device is not something a rendering test may depend on. In
            // inspection mode the permission state reports the status passed as its preview
            // status, which defaults to Granted, so the row renders without a runtime grant.
            CompositionLocalProvider(LocalInspectionMode provides true) {
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

    /** The max/min text of the day at [day], as the fixture data renders it. */
    private fun dayPair(day: Int): String = "${15 + day}\u00B0/${5 + day}\u00B0"
}
