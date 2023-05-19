package ovh.litapp.neurhome3

import android.os.*
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ovh.litapp.neurhome3.Navigator.NavTarget.ApplicationList
import ovh.litapp.neurhome3.Navigator.NavTarget.Home
import ovh.litapp.neurhome3.ui.applications.AllApplicationsScreen
import ovh.litapp.neurhome3.ui.home.HomeScreen
import ovh.litapp.neurhome3.ui.theme.Neurhome3Theme


private const val TAG = "NeurhomeMainActivity"

@RequiresApi(Build.VERSION_CODES.S)
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            Neurhome3Theme(backgroundAlpha = 0.1f) {
                Box(Modifier.systemBarsPadding()) {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = Home.label
                    ) {
                        composable(Home.label) {
                            HomeScreen(navController)
                        }
                        composable(ApplicationList.label) {
                            AllApplicationsScreen()
                        }
                    }
                }
            }
        }
    }
}


object Navigator {
    enum class NavTarget(val label: String) {
        Home("home"), ApplicationList("applicationList")
    }
}
