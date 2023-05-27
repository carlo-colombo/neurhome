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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import ovh.litapp.neurhome3.Navigator
import ovh.litapp.neurhome3.data.Application

@Composable
internal fun BottomBar(
    homeUiState: HomeUiState,
    viewModel: IHomeViewModel,
    navController: NavController
) {
    Row(
        horizontalArrangement = Arrangement.SpaceAround,
        modifier = Modifier
            .fillMaxWidth()
            .padding(5.dp)
    ) {
        val favouriteApps = homeUiState.favouriteApps

        @Composable
        fun AppOrDefault(app: Application?) {
            if (app != null) {
                IconButton(onClick = {
                    viewModel.appActions.launch(app.packageName, false)
                }) {
                    Icon(
                        painter = rememberDrawablePainter(app.icon),
                        contentDescription = app.label,
                        tint = Color.Unspecified,
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