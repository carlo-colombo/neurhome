package ovh.litapp.neurhome3.ui.applications

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import ovh.litapp.neurhome3.ui.AppViewModelProvider
import ovh.litapp.neurhome3.ui.components.ApplicationsList

private const val TAG = "AllApplicationsScreen"

@Composable
fun AllApplicationsScreen(
    viewModel: AllApplicationsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()
    Log.d(TAG, "$uiState")

    Column {
        ApplicationsList(
            list = uiState.allApps,
            launchApp = viewModel::launch,
            removeApp = viewModel::remove,
            toggleVisibility = viewModel::toggleVisibility,
            setFavourite = viewModel::setFavourite
        )
    }
}
