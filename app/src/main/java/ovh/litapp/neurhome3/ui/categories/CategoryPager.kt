package ovh.litapp.neurhome3.ui.categories

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import ovh.litapp.neurhome3.ui.components.ApplicationsList

const val UncategorizedScreenTestTag = "uncategorized-screen"
const val CategoryPagerTestTag = "category-pager"
const val TagNameInputTestTag = "tag-name-input"
const val CreateTagButtonTestTag = "create-tag-button"
const val RemoveTagButtonTestTag = "remove-tag-button"
const val ConfirmRemoveTagButtonTestTag = "confirm-remove-tag-button"

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
    val applicationsFlow = remember(viewModel, name) { viewModel.applicationsForTag(name) }
    val applications by applicationsFlow.collectAsStateWithLifecycle(emptyList())
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    var showRemoveConfirmation by remember(name) { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = name, style = MaterialTheme.typography.titleLarge)
                IconButton(
                    onClick = { showRemoveConfirmation = true },
                    modifier = Modifier.testTag(RemoveTagButtonTestTag),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove $name")
                }
            }
        }
        if (applications.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "No applications in $name")
            }
        } else {
            ApplicationsList(
                list = applications,
                appActions = viewModel.appActions,
                availableTags = tags.map { it.name },
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
            )
        }
    }
    if (showRemoveConfirmation) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirmation = false },
            title = { Text("Remove tag?") },
            text = { Text("Remove $name and all of its application assignments?") },
            confirmButton = {
                Button(
                    onClick = {
                        showRemoveConfirmation = false
                        viewModel.deleteTag(name)
                    },
                    modifier = Modifier.testTag(ConfirmRemoveTagButtonTestTag),
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirmation = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
fun UncategorizedScreen(
    modifier: Modifier = Modifier,
    onCreateTag: (String, (Boolean) -> Unit) -> Unit = { _, _ -> },
    viewModel: CategoryViewModel? = null,
) {
    val applicationsFlow = remember(viewModel) {
        viewModel?.applicationsForTag(null) ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }
    val applications by applicationsFlow
        .collectAsStateWithLifecycle(emptyList())
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val contentModifier = modifier.fillMaxSize().padding(16.dp)
    val tags by viewModel?.tags?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(emptyList()) }
    Column(
        modifier = contentModifier.testTag(UncategorizedScreenTestTag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; error = null },
                label = { Text("New tag") },
                isError = error != null,
                modifier = Modifier.weight(1f).testTag(TagNameInputTestTag),
            )
            IconButton(
                onClick = {
                    if (name.isBlank()) error = "Tag name cannot be blank"
                    else onCreateTag(name) { created ->
                        error = if (created) null else "Tag already exists"
                        if (created) name = ""
                    }
                },
                modifier = Modifier.testTag(CreateTagButtonTestTag),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add tag")
            }
        }
        error?.let { Text(it) }
        if (applications.isEmpty()) {
            Text("No uncategorized applications", modifier = Modifier.padding(top = 12.dp))
        } else {
            ApplicationsList(
                list = applications,
                appActions = viewModel?.appActions ?: ovh.litapp.neurhome3.ui.INeurhomeViewModel.AppActions(),
                availableTags = tags.map { it.name },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}
