package ovh.litapp.neurhome3.data

import android.content.Intent
import android.content.pm.LauncherActivityInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.graphics.vector.ImageVector

data class Application(
    val label: String = "",
    val packageName: String = "",
    val icon: Any?,
    val isVisible: Boolean = true,
    val score: Double = 0.0,
    val appInfo: LauncherActivityInfo? = null,
    val intent: Intent? = null,
    val visibility: ApplicationVisibility = ApplicationVisibility.VISIBLE
)

enum class ApplicationVisibility(val imageVector: ImageVector, val description: String) {

    VISIBLE(
        Icons.Default.Visibility, "Visible"
    ),
    HIDDEN_FROM_FILTERED(Icons.Default.FilterListOff, "Hidden when filtering"),
    HIDDEN_FROM_TOP(
        Icons.Default.FlashOff,
        "Hidden from top apps"
    );
}