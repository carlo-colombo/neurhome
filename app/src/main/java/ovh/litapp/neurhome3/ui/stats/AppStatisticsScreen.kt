package ovh.litapp.neurhome3.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.ui.AppViewModelProvider
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun AppStatisticsScreen(
    viewModel: AppStatisticsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "App Usage Simulator",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        UsageFilters(
            uiState = uiState,
            onDaySelected = viewModel::onDaySelected,
            onTimeSelected = viewModel::onTimeSelected,
            onWifiSelected = viewModel::onWifiSelected
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.usedApps) { app ->
                AppStatItem(app)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
fun UsageFilters(
    uiState: AppStatisticsUiState,
    onDaySelected: (Int) -> Unit,
    onTimeSelected: (LocalTime) -> Unit,
    onWifiSelected: (String?) -> Unit
) {
    val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Day of Week", style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(days) { index, day ->
                FilterChip(
                    selected = uiState.selectedDay == index,
                    onClick = { onDaySelected(index) },
                    label = { Text(day) }
                )
            }
        }

        Text(
            "Time: ${uiState.selectedTime.format(DateTimeFormatter.ofPattern("HH:mm"))}",
            style = MaterialTheme.typography.titleSmall
        )
        Slider(
            value = (uiState.selectedTime.hour * 60 + uiState.selectedTime.minute).toFloat(),
            onValueChange = {
                val totalMinutes = it.toInt()
                onTimeSelected(LocalTime.of(totalMinutes / 60, totalMinutes % 60))
            },
            valueRange = 0f..1439f
        )

        Text("Wifi / SSID", style = MaterialTheme.typography.titleSmall)
        var expanded by remember { mutableStateOf(false) }
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(uiState.selectedWifi ?: "Any / No Wifi")
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("Any / No Wifi") },
                    onClick = {
                        onWifiSelected(null)
                        expanded = false
                    }
                )
                uiState.availableSSIDs.forEach { ssid ->
                    DropdownMenuItem(
                        text = { Text(ssid) },
                        onClick = {
                            onWifiSelected(ssid)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun AppStatItem(app: Application) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (app.icon != null) {
                GlideImage(
                    model = app.icon,
                    contentDescription = app.label,
                    modifier = Modifier.size(48.dp)
                )
            } else {
                Icon(
                    painter = rememberVectorPainter(image = Icons.Default.Person),
                    contentDescription = app.label,
                    modifier = Modifier.size(48.dp)
                )
            }
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(text = app.label, style = MaterialTheme.typography.bodyLarge)
                Text(text = app.packageName, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(
            text = "%.0f".format(app.score),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
