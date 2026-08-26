package ovh.litapp.neurhome3.ui.applications

import android.util.Log
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
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import ovh.litapp.neurhome3.ui.AppViewModelProvider
import ovh.litapp.neurhome3.ui.components.ApplicationsList

private const val TAG = "AllApplicationsScreen"

@Composable
fun AllApplicationsScreen(
    onOpenSettings: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenStatistics: () -> Unit,
    viewModel: AllApplicationsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()
    Log.d(TAG, "$uiState")

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
            appActions = viewModel.appActions,
            availableTags = uiState.tags,
        )
    }
}
