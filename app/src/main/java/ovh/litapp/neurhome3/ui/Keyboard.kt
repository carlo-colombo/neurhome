package ovh.litapp.neurhome3.ui

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "Keyboard"

@Composable
fun Keycap(
    modifier: Modifier = Modifier,
    border: Boolean = true,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()


    Box(
        modifier = modifier
            .padding(5.dp)
            .height(55.dp)
            .border(
                1.dp,
                color = if (border) Color.LightGray else Color.Unspecified,
                shape = RoundedCornerShape(8.dp)
            )
            .background(if (pressed) Color(0xff1960a5) else Color.Unspecified)
            .clickable {
                pressed = true
                scope.launch {
                    delay(300)
                    pressed = false
                }
                onClick()
            }, contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Keyboard(
    appsViewModel: ApplicationsViewModel? = null,
    appsUiState: ApplicationsUiState = ApplicationsUiState(
        query = mutableListOf(
            "asdasd",
            "asdasd",
            "asdasd",
            "asdasd",
            "asdasd",
            "asdasd",
            "asdasd",
            "asdasd",
            "asdasd",
            "asdasd",
            "asdasd",
            "asdasd",
            "asdasd"
        )
    )
) {
    Column(
        verticalArrangement = Arrangement.Bottom, modifier = Modifier.padding(horizontal = 15.dp)
    ) {
        val key: @Composable (String) -> Unit = { s: String ->
            Keycap(modifier = Modifier.weight(1f), onClick = {
                appsViewModel?.vibrate?.invoke()
                appsViewModel?.push("[${s}]")
            }) {
                Text(s, style = MaterialTheme.typography.titleLarge)
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
                IconButton(onClick = { appsViewModel?.clearQuery() }) {
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
            Keycap(modifier = Modifier.weight(1f), border = false, onClick = {
                appsViewModel?.pop()
                appsViewModel?.vibrate?.invoke()
            }) {
                Icon(
                    Icons.Default.Backspace, contentDescription = "Backspace"
                )
            }
        }

    }
}


@Composable
@Preview(backgroundColor = 0xf000)
fun KeyboardPreview() {
    Surface(
        color = Color.Black, contentColor = Color.White
    ) {
        Keyboard()
    }
}