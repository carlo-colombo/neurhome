package ovh.litapp.neurhome3.ui.categories

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

const val UncategorizedScreenTestTag = "uncategorized-screen"
const val CategoryPagerTestTag = "category-pager"

/** The home surface followed by the category screens. */
@Composable
fun CategoryPager(
    homeContent: @Composable (onSwipeLeft: () -> Unit) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = 0) { 2 }
    val coroutineScope = rememberCoroutineScope()
    val openUncategorized: () -> Unit = {
        coroutineScope.launch {
            pagerState.animateScrollToPage(1)
        }
    }

    BackHandler(enabled = pagerState.currentPage == 0) {
        openUncategorized()
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxSize()
            .testTag(CategoryPagerTestTag)
            .pointerInput(openUncategorized) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                    var triggered = false
                    do {
                        val event = awaitPointerEvent(pass = PointerEventPass.Final)
                        val change = event.changes.firstOrNull() ?: break
                        if (!triggered &&
                            down.position.y > size.height * 0.35f &&
                            change.position.x - down.position.x < -50f
                        ) {
                            triggered = true
                            openUncategorized()
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) { page ->
        when (page) {
            0 -> homeContent(openUncategorized)
            1 -> UncategorizedScreen()
        }
    }
}

@Composable
fun UncategorizedScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(UncategorizedScreenTestTag)
            .padding(16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(text = "Uncategorized")
    }
}
