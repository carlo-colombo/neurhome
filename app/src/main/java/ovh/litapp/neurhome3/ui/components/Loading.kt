package ovh.litapp.neurhome3.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun Loading(
    modifier: Modifier = Modifier,
    loading: Boolean = true,
    content: @Composable () -> Unit
) {
    if (loading) {
        Box(
            modifier = modifier
        ) {
            CircularProgressIndicator(
                modifier = modifier,
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    } else (content())
}