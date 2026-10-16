package ovh.litapp.neurhome3.ui.stats

import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the slider debounce of [AppStatisticsViewModel] on virtual time: the ranking query is
 * keyed on the debounced slider value, so a drag must not turn every tick into one of the ranking
 * queries, and a settled slider must still reach the query exactly once.
 */
class AppStatisticsViewModelTest {
    @Test
    fun aSliderDragIsQueriedOnceAfterTheDragSettles() = runTest {
        val slider = MutableStateFlow(LocalTime.of(9, 0))
        val queried = mutableListOf<LocalTime>()
        val collection = launch { slider.debounceSliderQueries().collect { queried += it } }
        runCurrent()

        // One drag: a tick every 10 ms, each of them well inside the debounce window. Nothing may
        // reach the query yet, not even the value the drag started from.
        for (minute in 1..5) {
            slider.value = LocalTime.of(9, minute)
            advanceTimeBy(10)
        }
        assertEquals(emptyList<LocalTime>(), queried)

        // The drag settles: only the position it came to rest on is queried.
        advanceTimeBy(SLIDER_QUERY_DEBOUNCE_MILLIS)
        runCurrent()
        assertEquals(listOf(LocalTime.of(9, 5)), queried)

        collection.cancel()
    }

    @Test
    fun aSettledSliderValueReachesTheQuery() = runTest {
        val slider = MutableStateFlow(LocalTime.of(7, 0))
        val queried = mutableListOf<LocalTime>()
        val collection = launch { slider.debounceSliderQueries().collect { queried += it } }
        runCurrent()

        slider.value = LocalTime.of(7, 30)
        advanceTimeBy(SLIDER_QUERY_DEBOUNCE_MILLIS)
        runCurrent()

        assertEquals(listOf(LocalTime.of(7, 30)), queried)

        collection.cancel()
    }
}
