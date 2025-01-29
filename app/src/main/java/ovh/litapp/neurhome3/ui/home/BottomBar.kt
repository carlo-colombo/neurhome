package ovh.litapp.neurhome3.ui.home

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import ovh.litapp.neurhome3.Navigator
import ovh.litapp.neurhome3.data.Application

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
internal fun BottomBar(
    favouriteApps: Map<Int, Application>,
    viewModel: IHomeViewModel,
    navController: NavController
) {
    Row(
        horizontalArrangement = Arrangement.SpaceAround,
        modifier = Modifier
            .fillMaxWidth()
            .padding(5.dp)
    ) {
        @Composable
        fun AppOrDefault(app: Application?) {
            if (app != null) {
                IconButton(onClick = {
                    viewModel.appActions.launch(app, false)
                }) {
                    GlideImage(
                        model = app.icon,
                        contentDescription = app.label,
                        modifier = Modifier
                            .size(40.dp)
                    )
                }
            } else {
                IconButton(onClick = {}) {
                    Icon(
                        Icons.Default.AddCircle,
                        contentDescription = "No Favourite Set",
                        modifier = Modifier
                            .size(40.dp)
                    )
                }
            }
        }


        AppOrDefault(app = favouriteApps[1])
        AppOrDefault(app = favouriteApps[2])
        IconButton(onClick = {
            navController.navigate(Navigator.NavTarget.ApplicationList.label)
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