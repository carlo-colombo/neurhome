package ovh.litapp.neurhome3.ui.home

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.stableListKey
import ovh.litapp.neurhome3.ui.INeurhomeViewModel
import ovh.litapp.neurhome3.ui.components.ApplicationItem

@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun HomeApplicationsList(
    list: List<Application>,
    appActions: INeurhomeViewModel.AppActions,
    filtering: Boolean,
) {
    val permission = rememberPermissionState(Manifest.permission.CALL_PHONE)

    LazyColumn(
        verticalArrangement = if (filtering) Arrangement.Bottom else Arrangement.Top,
        modifier = Modifier.fillMaxHeight()
    ) {
        items(list, key = { it.stableListKey() }) { app ->
            ApplicationItem(app, appActions, permission)
        }
    }
}
