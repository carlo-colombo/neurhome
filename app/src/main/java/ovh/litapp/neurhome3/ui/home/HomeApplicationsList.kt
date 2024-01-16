package ovh.litapp.neurhome3.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.ui.INeurhomeViewModel
import ovh.litapp.neurhome3.ui.components.ApplicationItem

@Composable
internal fun HomeApplicationsList(
    list: List<Application>,
    appActions: INeurhomeViewModel.AppActions,
    filtering: Boolean,
) {
    Column(
        verticalArrangement = if (filtering) Arrangement.Bottom else Arrangement.Top,
        modifier = Modifier.fillMaxHeight()
    ) {
        list.forEach { app ->
            ApplicationItem(
                app = app,
                appActions = appActions,
            )
        }
    }
}