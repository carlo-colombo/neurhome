package ovh.litapp.neurhome3.ui.home

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import ovh.litapp.neurhome3.Navigator
import ovh.litapp.neurhome3.ui.AppViewModelProvider
import ovh.litapp.neurhome3.ui.components.Applications
import ovh.litapp.neurhome3.ui.components.Keyboard
import ovh.litapp.neurhome3.ui.components.Watch
import ovh.litapp.neurhome3.ui.components.ph

private const val TAG = "HomeScreen"

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val homeUiState by viewModel.homeUiState.collectAsState()
    Log.d(TAG, "$homeUiState")

    BackHandler(true) {}
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Watch()
        Applications(
            list = homeUiState.homeApps,
            launchApp = viewModel::launch,
            removeApp = viewModel::remove,
        )
        Column {
            Keyboard(appsViewModel = viewModel, appsUiState = homeUiState)
            Spacer(modifier = Modifier.height(5.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceAround,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(5.dp)
            ) {
                ph()
                ph()
                IconButton(onClick = {
                    navController.navigate(Navigator.NavTarget.ApplicationList.label)
                }) {
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