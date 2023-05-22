package ovh.litapp.neurhome3.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import ovh.litapp.neurhome3.ui.AppViewModelProvider

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()
    Column(
        Modifier
            .padding(2.dp)
            .fillMaxSize()
    ) {
        LogWiFi(uiState.logWiFi, viewModel::toggleWifi)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LogWiFi(logWiFi: Boolean, toggle: () -> Unit) {
    val locationPermission = rememberPermissionState(
        android.Manifest.permission.ACCESS_FINE_LOCATION
    )

    if (logWiFi && !locationPermission.status.isGranted) {
        SideEffect {
            locationPermission.launchPermissionRequest()
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = "Log Wi-Fi access point name")
        Checkbox(
            checked = logWiFi, onCheckedChange = { isChecked ->
                if (isChecked && !locationPermission.status.isGranted) {
                    locationPermission.launchPermissionRequest()
                }

                toggle()
            }, colors = CheckboxDefaults.colors(
                uncheckedColor = Color.White
            )
        )
    }
}