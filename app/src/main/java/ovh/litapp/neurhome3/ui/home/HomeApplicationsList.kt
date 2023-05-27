package ovh.litapp.neurhome3.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.ui.INeurhomeViewModel
import ovh.litapp.neurhome3.ui.NeurhomeViewModel
import ovh.litapp.neurhome3.ui.components.ApplicationItem

@Composable
internal fun HomeApplicationsList(
    list: List<Application>,
    appActions: INeurhomeViewModel.AppActions,
) {
    Column(
        verticalArrangement = Arrangement.Bottom
    ) {
        list.forEach { app ->
            ApplicationItem(
                app = app,
                appActions = appActions,
            )
        }
    }
}