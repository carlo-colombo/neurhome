package ovh.litapp.neurhome3.ui.applications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ovh.litapp.neurhome3.application.AppViewModelFactory
import ovh.litapp.neurhome3.ui.components.ApplicationsList

@Composable
fun AllApplicationsScreen(
    onOpenSettings: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenStatistics: () -> Unit,
    viewModel: AllApplicationsViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column {
        Row {
            IconButton(onClick = {
                onOpenSettings()
            }) {
                Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
            }
            IconButton(onClick = {
                onOpenCategories()
            }) {
                Icon(imageVector = Icons.Default.Category, contentDescription = "Categories")
            }
            IconButton(onClick = {
                onOpenStatistics()
            }) {
                Icon(imageVector = Icons.Default.Info, contentDescription = "Statistics")
            }
        }
        ApplicationsList(
            list = uiState.allApps,
            onLaunch = viewModel.appActions.launch,
            managementActions = viewModel.appActions.management,
            tagActions = viewModel.appActions.tags,
            availableTags = uiState.tags,
        )
    }
}
