package ovh.litapp.neurhome3.ui.categories

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ovh.litapp.neurhome3.ui.AppViewModelProvider

const val UncategorizedScreenTestTag = "uncategorized-screen"
const val CategoryPagerTestTag = "category-pager"
const val TagNameInputTestTag = "tag-name-input"
const val CreateTagButtonTestTag = "create-tag-button"

@Composable
fun CategoryPager(
    viewModel: CategoryViewModel = viewModel(factory = AppViewModelProvider.Factory),
    includeHome: Boolean = true,
    homeContent: @Composable (onSwipeLeft: () -> Unit) -> Unit,
) {
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(initialPage = 0) { tags.size + if (includeHome) 2 else 1 }
    val coroutineScope = rememberCoroutineScope()
    var previousTagCount by remember { mutableStateOf(tags.size) }
    val uncategorizedPage = tags.size + if (includeHome) 1 else 0
    val openUncategorized: () -> Unit = {
        coroutineScope.launch { pagerState.animateScrollToPage(uncategorizedPage) }
    }

    LaunchedEffect(tags.size) {
        val wasOnUncategorized = pagerState.currentPage == previousTagCount + if (includeHome) 1 else 0
        previousTagCount = tags.size
        if (wasOnUncategorized) pagerState.scrollToPage(uncategorizedPage)
        if (pagerState.currentPage > uncategorizedPage) pagerState.scrollToPage(uncategorizedPage)
    }
    BackHandler(enabled = includeHome && pagerState.currentPage == 0) { openUncategorized() }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize().testTag(CategoryPagerTestTag).pointerInput(openUncategorized) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                var triggered = false
                do {
                    val event = awaitPointerEvent(pass = PointerEventPass.Final)
                    val change = event.changes.firstOrNull() ?: break
                    if (!triggered && down.position.y > size.height * .35f &&
                        change.position.x - down.position.x < -50f) {
                        triggered = true
                        openUncategorized()
                    }
                } while (event.changes.any { it.pressed })
            }
        },
    ) { page ->
        when {
            includeHome && page == 0 -> homeContent(openUncategorized)
            page < tags.size + if (includeHome) 1 else 0 ->
                TagScreen(tags[page - if (includeHome) 1 else 0].name)
            else -> UncategorizedScreen(onCreateTag = viewModel::createTag)
        }
    }
}

@Composable
fun TagScreen(name: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = name, style = MaterialTheme.typography.titleLarge)
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "Applications in $name")
            repeat(20) { Text(text = "Scroll content ${it + 1}") }
        }
    }
}

@Composable
fun UncategorizedScreen(
    modifier: Modifier = Modifier,
    onCreateTag: (String, (Boolean) -> Unit) -> Unit = { _, _ -> },
) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Uncategorized")
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; error = null },
            label = { Text("New tag") },
            isError = error != null,
            modifier = Modifier.fillMaxWidth().testTag(TagNameInputTestTag),
        )
        Button(
            onClick = {
                if (name.isBlank()) error = "Tag name cannot be blank"
                else onCreateTag(name) { created ->
                    error = if (created) null else "Tag already exists"
                    if (created) name = ""
                }
            },
            modifier = Modifier.testTag(CreateTagButtonTestTag),
        ) { Text("Create tag") }
        error?.let { Text(it) }
    }
}
