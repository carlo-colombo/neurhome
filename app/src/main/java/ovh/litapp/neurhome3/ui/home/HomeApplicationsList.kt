package ovh.litapp.neurhome3.ui.home

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    onLaunch: (Application?, track: Boolean) -> Unit,
    filtering: Boolean,
    managementActions: INeurhomeViewModel.AppManagementActions,
    tagActions: INeurhomeViewModel.AppTagActions,
    availableTags: List<String> = emptyList(),
) {
    val permission = rememberPermissionState(Manifest.permission.CALL_PHONE)

    // fillMaxSize keeps the min height pinned to the slot, so while every row is collapsed the
    // column measures to the slot and the arrangement distributes exactly as before. A row that
    // opens grows past the slot and becomes reachable by scrolling instead of drawing over the
    // keyboard and the bottom bar.
    Column(
        verticalArrangement = if (filtering) Arrangement.Bottom else Arrangement.SpaceEvenly,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        list.take(MAX_HOME_APPLICATIONS).forEach { app ->
            key(app.stableListKey()) {
                ApplicationItem(
                    app = app,
                    onLaunch = onLaunch,
                    managementActions = managementActions,
                    tagActions = tagActions,
                    permission = permission,
                    manageEntry = true,
                    availableTags = availableTags,
                    compact = true,
                )
            }
        }
    }
}
