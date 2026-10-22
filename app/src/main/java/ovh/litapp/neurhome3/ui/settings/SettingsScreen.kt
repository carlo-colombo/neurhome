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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import ovh.litapp.neurhome3.data.repositories.ContactCandidates
import ovh.litapp.neurhome3.data.repositories.HomeAppSelection
import ovh.litapp.neurhome3.application.AppViewModelFactory
import java.time.ZoneId
import androidx.core.net.toUri

const val HomeAppSelectionSelectorTestTag = "home-app-selection-selector"
const val HomeAppSelectionClassicTestTag = "home-app-selection-classic"
const val HomeAppSelectionLearnedTestTag = "home-app-selection-learned"
const val HomeAppSelectionPrerequisiteTestTag = "home-app-selection-prerequisite"
const val ContactCandidatesSelectorTestTag = "contact-candidates-selector"
const val ContactCandidatesNoneTestTag = "contact-candidates-none"
const val ContactCandidatesStarredTestTag = "contact-candidates-starred"
const val ContactCandidatesAllTestTag = "contact-candidates-all"
const val SettingsAboutButtonTestTag = "settings-about-button"

@Composable
fun SettingsScreen(
    onDatabaseFileSelected: (Uri?) -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(
        Modifier
            .padding(2.dp)
            .fillMaxSize()
    ) {
        LogWiFi(uiState.logWiFi, viewModel::setWifiLogging)
        LogPosition(uiState.logPosition, viewModel::setPositionLogging)
        HomeAppRankingSection(uiState, viewModel::setHomeAppSelection)
        ShowCalendar(uiState.showCalendar, viewModel::setShowCalendar)
        ContactsSection(uiState.contactCandidates, viewModel::setContactCandidates)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(
                    value = uiState.showAlternativeTime,
                    role = Role.Checkbox,
                    onValueChange = { viewModel.toggleShowAlternativeTime() }
                )
        ) {
            Text(text = "Show alternative time")
            Checkbox(checked = uiState.showAlternativeTime, onCheckedChange = null)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        ) {
            val zones = remember { ZoneId.getAvailableZoneIds().sorted() }
            LargeDropdownMenu(
                label = "Time zone",
                notSetLabel = "Select a time zone",
                items = zones,
                selectedIndex = zones.indexOf(uiState.alternativeTimeZone),
                onItemSelected = { index, _ ->
                    viewModel.saveTimeZone(zones[index])
                },
            )
        }
        BackupStatusSection()
        ExportDatabase(context, viewModel::exportDatabase)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            val launcher =
                rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
                    onDatabaseFileSelected(it)
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
        Row {
            Button(
                onClick = onOpenAbout,
                modifier = Modifier.testTag(SettingsAboutButtonTestTag)
            ) {
                Text(text = "About")
            }
        }
    }
}

/** Wires the persisted selection and the current permission into the selector. */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun HomeAppRankingSection(
    uiState: SettingsUiState,
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
        SelectionOption(
            text = "Classic",
            testTag = HomeAppSelectionClassicTestTag,
            selected = selection == HomeAppSelection.CLASSIC,
            onSelect = { onSelect(HomeAppSelection.CLASSIC) }
        )
        SelectionOption(
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
private fun SelectionOption(
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
fun ShowCalendar(state: Boolean, setEnabled: (Boolean, Boolean) -> Unit) {
    SettingWithPermission(
        text = "Show Calendar",
        permissionString = Manifest.permission.READ_CALENDAR,
        permissionLabel = "Calendar",
        state = state,
        onSetEnabled = setEnabled
    )
}

/**
 * Wires the persisted contact scope to the contact permission. Contact rows can be read only while
 * the grant exists, so selecting a scope that reads them is an explicit permission request and is
 * persisted once the grant is observed; a revoked grant drops the scope back to
 * [ContactCandidates.NONE] instead of leaving a stale "all contacts" value behind.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun ContactsSection(
    selected: ContactCandidates,
    onSelect: (ContactCandidates, Boolean) -> Unit,
) {
    val permission = rememberPermissionState(Manifest.permission.READ_CONTACTS)
    val granted = permission.status.isGranted
    var pendingSelection by remember { mutableStateOf<ContactCandidates?>(null) }

    // No permission request may be launched from here: a selection waiting on a grant is written
    // after an explicit tap has launched the request.
    LaunchedEffect(granted) {
        val pending = pendingSelection
        when {
            granted && pending != null -> {
                pendingSelection = null
                onSelect(pending, true)
            }

            !granted && selected != ContactCandidates.NONE -> onSelect(ContactCandidates.NONE, false)
        }
    }

    ContactCandidatesSelector(
        selection = selected,
        onSelect = { candidates ->
            if (candidates == ContactCandidates.NONE || granted) {
                pendingSelection = null
                onSelect(candidates, granted)
            } else {
                pendingSelection = candidates
                permission.launchPermissionRequest()
            }
        }
    )
}

/**
 * The contact scope itself: one choice out of three, so "all contacts" and "starred contacts" can
 * never both be on. Stateless so Compose tests drive it without a permission or a contact list.
 */
@Composable
fun ContactCandidatesSelector(
    selection: ContactCandidates,
    onSelect: (ContactCandidates) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .testTag(ContactCandidatesSelectorTestTag)
    ) {
        Text(text = "Contacts for calls")
        SelectionOption(
            text = "Off",
            testTag = ContactCandidatesNoneTestTag,
            selected = selection == ContactCandidates.NONE,
            onSelect = { onSelect(ContactCandidates.NONE) }
        )
        SelectionOption(
            text = "Starred contacts",
            testTag = ContactCandidatesStarredTestTag,
            selected = selection == ContactCandidates.STARRED,
            onSelect = { onSelect(ContactCandidates.STARRED) }
        )
        SelectionOption(
            text = "All contacts",
            testTag = ContactCandidatesAllTestTag,
            selected = selection == ContactCandidates.ALL,
            onSelect = { onSelect(ContactCandidates.ALL) }
        )
    }
}

@Composable
fun LogPosition(position: Boolean, setEnabled: (Boolean, Boolean) -> Unit) {
    SettingWithPermission(
        text = "Log position",
        permissionString = Manifest.permission.ACCESS_FINE_LOCATION,
        permissionLabel = "Location",
        state = position,
        onSetEnabled = setEnabled
    )
}

@Composable
fun LogWiFi(logWiFi: Boolean, setEnabled: (Boolean, Boolean) -> Unit) {
    SettingWithPermission(
        text = "Log Wi-Fi access point name",
        permissionString = Manifest.permission.ACCESS_FINE_LOCATION,
        permissionLabel = "Location",
        state = logWiFi,
        onSetEnabled = setEnabled
    )
}

/**
 * A permission-backed opt-in. The persisted value is written only while the backing grant exists:
 * turning it off is always saved, turning it on is an explicit permission request that saves once
 * the grant is observed, and a revoked grant turns the stale opt-in off. The whole row is a single
 * toggle target so the label is not inert next to the switch.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SettingWithPermission(
    text: String,
    permissionString: String,
    permissionLabel: String,
    state: Boolean,
    onSetEnabled: (enabled: Boolean, permissionGranted: Boolean) -> Unit,
) {
    val permission = rememberPermissionState(permission = permissionString)
    val granted = permission.status.isGranted
    var pendingGrantWrite by remember { mutableStateOf(false) }

    // No permission request may be launched from here: the write waiting on a grant is observed
    // after an explicit tap has launched the request.
    LaunchedEffect(granted) {
        when {
            granted && pendingGrantWrite -> {
                pendingGrantWrite = false
                onSetEnabled(true, true)
            }

            !granted && state -> onSetEnabled(false, false)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = state,
                role = Role.Checkbox,
                onValueChange = { wantsOn ->
                    when {
                        !wantsOn -> onSetEnabled(false, granted)
                        granted -> onSetEnabled(true, true)
                        else -> {
                            pendingGrantWrite = true
                            permission.launchPermissionRequest()
                        }
                    }
                }
            )
            .semantics {
                stateDescription = if (granted) {
                    if (state) "On" else "Off"
                } else {
                    "Off, permission not granted"
                }
            }
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = text)
            if (!granted) {
                Text(
                    text = "Requires $permissionLabel access",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Checkbox(checked = state, onCheckedChange = null)
    }
}
