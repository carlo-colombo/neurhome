package ovh.litapp.neurhome3

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ovh.litapp.neurhome3.Navigator.NavTarget.Home
import ovh.litapp.neurhome3.Navigator.NavTarget.Settings
import ovh.litapp.neurhome3.application.NeurhomeApplication
import ovh.litapp.neurhome3.ui.applications.AllApplicationsScreen
import ovh.litapp.neurhome3.ui.home.HomeScreen
import ovh.litapp.neurhome3.ui.settings.SettingsScreen
import ovh.litapp.neurhome3.ui.theme.Neurhome3Theme
import kotlin.system.exitProcess


const val TAG = "NeurhomeMainActivity"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            Neurhome3Theme(dynamicColor = true) {
                Box(
                    Modifier
                        .systemBarsPadding()
                        .padding(horizontal = 10.dp)
                ) {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController, startDestination = Home.label
                    ) {
                        composable(Home.label) {
                            HomeScreen(navController)
                        }
                        composable(Navigator.NavTarget.ApplicationList.label) {
                            AllApplicationsScreen(navController)
                        }
                        composable(Settings.label) {
                            SettingsScreen({ u: Uri? ->
                                if (u != null) {
                                    Log.d(TAG, "Replacing database")
                                    (this@MainActivity.application as NeurhomeApplication)
                                        .replaceDatabase(u)
                                    Log.d(TAG, "Restarting Neurhome")
                                    this@MainActivity.restart()
                                }
                            })
                        }
                    }
                }
            }
        }
    }

    private fun restart() {
        val context = this@MainActivity
        val intent = Intent(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        context.finish()

        exitProcess(0)
    }
}


object Navigator {
    enum class NavTarget(val label: String) {
        Home("home"), ApplicationList("applicationList"), Settings("settings")
    }
}
