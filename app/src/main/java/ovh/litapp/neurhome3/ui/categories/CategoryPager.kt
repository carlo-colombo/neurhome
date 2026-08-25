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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import ovh.litapp.neurhome3.data.stableListKey

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
                TagScreen(tags[page - if (includeHome) 1 else 0].name, viewModel)
            else -> UncategorizedScreen(onCreateTag = viewModel::createTag, viewModel = viewModel)
        }
    }
}

@Composable
fun TagScreen(name: String, viewModel: CategoryViewModel, modifier: Modifier = Modifier) {
    val applications by viewModel.applicationsForTag(name).collectAsStateWithLifecycle(emptyList())
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = name, style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Text(text = "Applications in $name") }
            items(applications, key = { it.stableListKey() }) { app ->
                Text(text = app.alias.ifBlank { app.label })
            }
        }
    }
}

@Composable
fun UncategorizedScreen(
    modifier: Modifier = Modifier,
    onCreateTag: (String, (Boolean) -> Unit) -> Unit = { _, _ -> },
    viewModel: CategoryViewModel? = null,
) {
    val applications by (viewModel?.applicationsForTag(null) ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .collectAsStateWithLifecycle(emptyList())
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(text = "Uncategorized") }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; error = null },
                label = { Text("New tag") },
                isError = error != null,
                modifier = Modifier.fillMaxWidth().testTag(TagNameInputTestTag),
            )
        }
        item {
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
        }
        error?.let { message -> item { Text(message) } }
        items(applications, key = { it.stableListKey() }) { app ->
            Text(text = app.alias.ifBlank { app.label })
        }
    }
}
