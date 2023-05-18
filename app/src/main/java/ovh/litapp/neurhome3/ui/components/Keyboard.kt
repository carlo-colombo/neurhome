package ovh.litapp.neurhome3.ui.components

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ovh.litapp.neurhome3.ui.home.HomeUiState
import ovh.litapp.neurhome3.ui.home.HomeViewModel

private const val TAG = "Keyboard"

@Composable
fun Keycap(
    onClick: () -> Unit = {},
    border: Boolean = true,
    content: @Composable () -> Unit,
) {
    Button(
        onClick = onClick,
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(25f),
        contentPadding = PaddingValues(),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        border = if (border) BorderStroke(width = 1.dp, color = Color.White) else null
    ) {
        content()
    }
}

@Composable
fun Keyboard(
    appsViewModel: HomeViewModel,
    appsUiState: HomeUiState
) {
    Column() {
        val modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(2.dp)
        val key: @Composable (String) -> Unit = { s: String ->
            Box(modifier = modifier) {
                Keycap(onClick = {
                    appsViewModel.push("[${s}]")
                }) {
                    Text(s, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Log.d(TAG, "${appsUiState.query}")
            Text(
                text = appsUiState.query.joinToString(""),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f, true)
            )
            if (appsUiState.query.size > 0) {
                IconButton(onClick = { appsViewModel.clearQuery() }) {
                    Icon(
                        Icons.Default.Cancel, contentDescription = "Clear query"
                    )
                }
            }
        }
        Row {
            listOf("0-9", "abc", "def", "ghi", "jkl").forEach { key(it) }
        }
        Row {
            listOf("mno", "pqrs", "tuv", "wxyz").forEach { key(it) }
            Box(
                modifier = modifier
            ) {
                Keycap(border = false, onClick = {
                    appsViewModel.pop()
                }) {
                    Icon(
                        Icons.Default.Backspace, contentDescription = "Backspace"
                    )
                }
            }
        }
    }
}
//
//@Composable
//@Preview(backgroundColor = 0xf000)
//fun Keyboard2Preview() {
//    Surface(
//        color = Color.Black, contentColor = Color.White
//    ) {
//        Keyboard()
//    }
//}
