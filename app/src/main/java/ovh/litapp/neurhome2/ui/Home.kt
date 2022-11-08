package ovh.litapp.neurhome2.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun Home(
    onAppsClick: () -> Unit,
    appsViewModel: ApplicationsViewModel
) {
    BackHandler(true) {}
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Watch()
        Keyboard(appsViewModel = appsViewModel)
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