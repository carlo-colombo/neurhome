package ovh.litapp.neurhome3.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ovh.litapp.neurhome3.ui.home.FilteredUIState
import ovh.litapp.neurhome3.ui.home.IHomeViewModel

private const val KEYBOARD_ROW_HEIGHT = 59
private const val KEYCAP_HEIGHT = 55

@Composable
fun Keycap(
    onClick: () -> Unit = {},
    border: Boolean = true,
    content: @Composable () -> Unit,
) {
    Button(
        onClick = onClick,
        Modifier
            .fillMaxWidth()
            .height(KEYCAP_HEIGHT.dp),
        shape = RoundedCornerShape(25f),
        contentPadding = PaddingValues(),
        colors = ButtonDefaults.outlinedButtonColors(),
        border = if (border) BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        ) else null
    ) {
        content()
    }
}

@Composable
fun Keyboard(
    appsViewModel: IHomeViewModel,
    appsUiState: FilteredUIState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (appsUiState.query.isNotEmpty()) {
            KeyboardInputRow(appsViewModel, appsUiState)
        }
        KeyboardFirstRow(appsViewModel)
        KeyboardSecondRow(appsViewModel)
    }
}

@Composable
private fun KeyboardInputRow(
    appsViewModel: IHomeViewModel,
    appsUiState: FilteredUIState,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = appsUiState.query.joinToString(""),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = {
            appsViewModel.clearQuery()
            appsViewModel.vibrate()
        }) {
            Icon(Icons.Default.Cancel, contentDescription = "Clear query")
        }
    }
}

@Composable
private fun KeyboardFirstRow(appsViewModel: IHomeViewModel) {
    KeyboardKeyRow(appsViewModel, listOf("0-9", "abc", "def", "ghi", "jkl"))
}

@Composable
private fun KeyboardSecondRow(appsViewModel: IHomeViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(KEYBOARD_ROW_HEIGHT.dp),
    ) {
        listOf("mno", "pqrs", "tuv", "wxyz").forEach { key ->
            KeyboardKey(appsViewModel, key, Modifier.weight(1f))
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(2.dp)) {
            Keycap(border = false, onClick = {
                appsViewModel.vibrate()
                appsViewModel.pop()
            }) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace")
            }
        }
    }
}

@Composable
private fun KeyboardKeyRow(appsViewModel: IHomeViewModel, keys: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(KEYBOARD_ROW_HEIGHT.dp),
    ) {
        keys.forEach { key ->
            KeyboardKey(appsViewModel, key, Modifier.weight(1f))
        }
    }
}

@Composable
private fun KeyboardKey(
    appsViewModel: IHomeViewModel,
    key: String,
    modifier: Modifier,
) {
    Box(modifier = modifier.fillMaxHeight().padding(2.dp)) {
        Keycap(onClick = {
            appsViewModel.vibrate()
            appsViewModel.push("[$key]")
        }) {
            Text(key, style = MaterialTheme.typography.titleLarge)
        }
    }
}
