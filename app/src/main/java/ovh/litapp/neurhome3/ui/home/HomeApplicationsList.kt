package ovh.litapp.neurhome3.ui.home

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.stableListKey
import ovh.litapp.neurhome3.ui.INeurhomeViewModel
import ovh.litapp.neurhome3.ui.components.ApplicationItem

private const val MAX_HOME_APPLICATIONS = 6

@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun HomeApplicationsList(
    list: List<Application>,
    appActions: INeurhomeViewModel.AppActions,
    filtering: Boolean,
) {
    val permission = rememberPermissionState(Manifest.permission.CALL_PHONE)

    Column(
        verticalArrangement = if (filtering) Arrangement.Bottom else Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxSize()
    ) {
        list.take(MAX_HOME_APPLICATIONS).forEach { app ->
            key(app.stableListKey()) {
                ApplicationItem(app, appActions, permission, compact = true)
            }
        }
    }
}
