package ovh.litapp.neurhome3.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.ui.components.ApplicationIcon

@Composable
internal fun BottomBar(
    favouriteApps: Map<Int, Application>,
    viewModel: IHomeViewModel,
    onOpenApplications: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceAround,
        modifier = Modifier
            .fillMaxWidth()
            .padding(5.dp)
    ) {
        @Composable
        fun AppOrDefault(app: Application?) {
            if (app == null) {
                // Static placeholder: an empty favourite slot must not be announced as a live button.
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.AddCircle,
                        contentDescription = "No Favourite Set",
                        modifier = Modifier.size(40.dp)
                    )
                }
                return
            }

            IconButton(onClick = {
                viewModel.appActions.launch(app, false)
            }) {
                // A favourite contact has no launcher icon; its photo is decoded when the slot is
                // composed instead of when the contact list is read.
                ApplicationIcon(
                    app = app,
                    size = 40.dp,
                    contentDescription = app.label,
                )
            }
        }


        AppOrDefault(app = favouriteApps[1])
        AppOrDefault(app = favouriteApps[2])
        IconButton(onClick = {
            onOpenApplications()
        }) {
            Icon(
                Icons.Default.Apps, contentDescription = "All Apps",
                modifier = Modifier.size(40.dp)
            )
        }
        AppOrDefault(app = favouriteApps[3])
        AppOrDefault(app = favouriteApps[4])
    }
}
