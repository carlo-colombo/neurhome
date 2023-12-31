package ovh.litapp.neurhome3.data

import android.content.pm.LauncherActivityInfo
import android.graphics.drawable.Drawable

data class Application(
    val label: String = "",
    val packageName: String = "",
    val icon: Drawable,
    val isVisible: Boolean = true,
    val count: Int = 0,
    val appInfo: LauncherActivityInfo? = null
)