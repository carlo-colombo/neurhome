package ovh.litapp.neurhome3.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import ovh.litapp.neurhome3.data.repositories.HomeAppSelection
import ovh.litapp.neurhome3.ui.AppViewModelProvider
import java.time.ZoneId
import androidx.core.net.toUri

const val HomeAppSelectionSelectorTestTag = "home-app-selection-selector"
const val HomeAppSelectionClassicTestTag = "home-app-selection-classic"
const val HomeAppSelectionLearnedTestTag = "home-app-selection-learned"
const val HomeAppSelectionPrerequisiteTestTag = "home-app-selection-prerequisite"

@Composable
fun SettingsScreen(
    restart: (Uri?) -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    Column(
        Modifier
            .padding(2.dp)
            .fillMaxSize()
    ) {
        LogWiFi(uiState.logWiFi, viewModel::toggleWifi)
        LogPosition(uiState.logPosition, viewModel::toggleLogPosition)
        HomeAppRankingSection(uiState, viewModel::setHomeAppSelection)
        ShowCalendar(uiState.showCalendar, viewModel::toggleShowCalendar)
        ShowStarredContacts(uiState.starredContacts, viewModel::toggleShowStarredContacts)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Show alternative time")
                    Checkbox(checked = uiState.showAlternativeTime, onCheckedChange = { isChecked ->
                        viewModel.toggleShowAlternativeTime()
                    })
                }
                var zones = ZoneId.getAvailableZoneIds().sortedBy { it }.toList<String>()
                var selectedZone = uiState.alternativeTimeZone
                var selectedIndex = zones.indexOf(selectedZone)

                LargeDropdownMenu(
                    label = "Time zone",
                    items = zones,
                    selectedIndex = selectedIndex,
                    onItemSelected = { index, _ ->
                        selectedZone = zones[index]
                        viewModel.saveTimeZone(selectedZone)
                    },
                )
            }
        }
        ExportDatabase(context, viewModel::exportDatabase)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            val launcher =
                rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
                    restart(it)
                }
            Text("Replace database")
            IconButton(onClick = { launcher.launch(arrayOf("application/octet-stream")) }) {
                Icon(imageVector = Icons.Default.Recycling, contentDescription = "Replace database")
            }

        }
        Row {
            val intent = remember {
                Intent(
                    Intent.ACTION_VIEW,
                    "https://github.com/carlo-colombo/neurhome/releases/latest".toUri()
                )
            }

            Button(onClick = { context.startActivity(intent) }) {
                Text(text = "Get latest release")
            }
        }
    }
}

/** Wires the persisted selection and the current permission into the selector. */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun HomeAppRankingSection(
    uiState: Settings,
    select: (HomeAppSelection) -> Unit
) {
    val permission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    HomeAppSelectionSelector(
        selection = uiState.homeAppSelection,
        prerequisites = LearnedRankingPrerequisites(
            wifiLogging = uiState.logWiFi,
            positionLogging = uiState.logPosition,
            locationPermissionGranted = permission.status.isGranted
        ),
        onRequestLocationPermission = { permission.launchPermissionRequest() },
        onSelect = select
    )
}

/**
 * Two-choice Home ranking selector. Choosing the learned ranking never enables
 * logging; unmet prerequisites are explained and only keep it inactive.
 */
@Composable
fun HomeAppSelectionSelector(
    selection: HomeAppSelection,
    prerequisites: LearnedRankingPrerequisites,
    onRequestLocationPermission: () -> Unit,
    onSelect: (HomeAppSelection) -> Unit
) {
    val explanation = prerequisites.explain(selection)
    Column(
        Modifier
            .fillMaxWidth()
            .testTag(HomeAppSelectionSelectorTestTag)
    ) {
        Text(text = "Home app ranking")
        HomeAppSelectionOption(
            text = "Classic",
            testTag = HomeAppSelectionClassicTestTag,
            selected = selection == HomeAppSelection.CLASSIC,
            onSelect = { onSelect(HomeAppSelection.CLASSIC) }
        )
        HomeAppSelectionOption(
            text = "Learned",
            testTag = HomeAppSelectionLearnedTestTag,
            selected = selection == HomeAppSelection.LEARNED,
            onSelect = {
                if (!prerequisites.locationPermissionGranted) onRequestLocationPermission()
                onSelect(HomeAppSelection.LEARNED)
            }
        )
        if (explanation != null) {
            Text(
                text = explanation,
                modifier = Modifier.testTag(HomeAppSelectionPrerequisiteTestTag)
            )
        }
    }
}

@Composable
private fun HomeAppSelectionOption(
    text: String,
    testTag: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .testTag(testTag)
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = text)
    }
}

@Composable
private fun ExportDatabase(
    context: Context,
    export: (Context) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Export database")
        IconButton(onClick = {
            export(context)
        }) {
            Icon(imageVector = Icons.Default.Share, contentDescription = "Export Database")
        }
    }
}


@Composable
fun ShowCalendar(state: Boolean, toggle: () -> Unit) {
    SettingWithPermission(
        text = "Show Calendar",
        permissionString = Manifest.permission.READ_CALENDAR,
        state = state,
        toggle = toggle
    )
}

@Composable
fun ShowStarredContacts(state: Boolean, toggle: () -> Unit) {
    SettingWithPermission(
        text = "Show Starred Contacts",
        permissionString = Manifest.permission.READ_CONTACTS,
        state = state,
        toggle = toggle
    )
}

@Composable
fun LogPosition(position: Boolean = false, toggle: () -> Unit = {}) {
    SettingWithPermission(
        text = "Log position",
        permissionString = Manifest.permission.ACCESS_FINE_LOCATION,
        state = position,
        toggle = toggle
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LogWiFi(logWiFi: Boolean, toggle: () -> Unit) {
    SettingWithPermission(
        text = "Log Wi-Fi access point name",
        permissionString = Manifest.permission.ACCESS_FINE_LOCATION,
        state = logWiFi,
        toggle = toggle
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SettingWithPermission(
    text: String, permissionString: String, toggle: () -> Unit, state: Boolean = false
) {
    val permission = rememberPermissionState(
        permission = permissionString
    )
    if (state && !permission.status.isGranted) {
        SideEffect {
            permission.launchPermissionRequest()
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = text)
        Checkbox(checked = state && permission.status.isGranted, onCheckedChange = { isChecked ->
            if (isChecked && !permission.status.isGranted) {
                permission.launchPermissionRequest()
            }

            toggle()
        })
    }
}
