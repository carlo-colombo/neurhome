package ovh.litapp.neurhome3.data

import android.content.Intent
import android.content.pm.LauncherActivityInfo

data class Application(
    val label: String = "",
    val packageName: String = "",
    val icon: Any?,
    val isVisible: Boolean = true,
    val count: Int = 0,
    val appInfo: LauncherActivityInfo? = null,
    val intent: Intent? = null,
)
