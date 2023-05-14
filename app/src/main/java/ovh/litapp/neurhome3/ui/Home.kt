package ovh.litapp.neurhome3.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.modifier.modifierLocalConsumer
import androidx.compose.ui.unit.dp

private const val TAG = "Home"

@Composable
fun Home(
    onAppsClick: () -> Unit,
    appsViewModel: ApplicationsViewModel,
    appsUiState: ApplicationsUiState
) {
    Log.d(TAG, "$appsUiState")
    BackHandler(true) {}
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Watch()
        Applications(list = appsUiState.homeApps, launchApp = appsViewModel::launch)
        Column {
            Keyboard(appsViewModel = appsViewModel, appsUiState = appsUiState)
            Spacer( modifier = Modifier.height(5.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceAround,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(5.dp)
            ) {
                ph()
                ph()
                IconButton(onClick = onAppsClick) {
                    Icon(
                        Icons.Default.Apps, contentDescription = "All Apps"
                    )
                }
                ph()
                ph()
            }
        }
    }
}