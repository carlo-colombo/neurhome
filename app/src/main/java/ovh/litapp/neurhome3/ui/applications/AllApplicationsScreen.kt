package ovh.litapp.neurhome3.ui.applications

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        Log.d(TAG, it.toString())

        viewModel.import(it)
    }

    Column {
        Row {
            IconButton(onClick = { launcher.launch(arrayOf("application/octet-stream")) }) {
                Icon(imageVector = Icons.Default.UploadFile, contentDescription = "Import database")
            }
        }
        ApplicationsList(
            list = uiState.allApps,
            launchApp = viewModel::launch,
            removeApp = viewModel::remove,
            toggleVisibility = viewModel::toggleVisibility,
            setFavourite = viewModel::setFavourite
        )
    }
}
