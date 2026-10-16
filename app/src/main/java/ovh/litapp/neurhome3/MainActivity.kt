package ovh.litapp.neurhome3

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ovh.litapp.neurhome3.Navigator.Destination.Home
import ovh.litapp.neurhome3.Navigator.Destination.Settings
import ovh.litapp.neurhome3.application.DatabaseReplacementResult
import ovh.litapp.neurhome3.application.NeurhomeApplication
import ovh.litapp.neurhome3.ui.applications.AllApplicationsScreen
import ovh.litapp.neurhome3.ui.categories.ApplicationCategoriesScreen
import ovh.litapp.neurhome3.ui.home.HomeScreen
import ovh.litapp.neurhome3.ui.settings.SettingsScreen
import ovh.litapp.neurhome3.ui.stats.AppStatisticsScreen
import ovh.litapp.neurhome3.ui.theme.Neurhome3Theme


const val TAG = "NeurhomeMainActivity"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            Neurhome3Theme(dynamicColor = true) {
                Box(
                    Modifier
                        .systemBarsPadding()
                        .padding(horizontal = 10.dp)
                        .semantics { testTagsAsResourceId = true }
                        .then(
                            if (BuildConfig.FLAVOR == "dev") {
                                Modifier.border(width = 1.dp, color = Color.Red)
                            } else {
                                Modifier
                            }
                        )
                ) {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController, startDestination = Home.route
                    ) {
                        composable(Home.route) {
                            HomeScreen(onOpenApplications = {
                                navController.navigateTo(Navigator.Destination.ApplicationList)
                            })
                        }
                        composable(Navigator.Destination.ApplicationList.route) {
                            AllApplicationsScreen(
                                onOpenSettings = { navController.navigateTo(Settings) },
                                onOpenCategories = { navController.navigateTo(Navigator.Destination.Categories) },
                                onOpenStatistics = { navController.navigateTo(Navigator.Destination.AppStatistics) },
                            )
                        }
                        composable(Navigator.Destination.Categories.route) {
                            ApplicationCategoriesScreen()
                        }
                        composable(Navigator.Destination.AppStatistics.route) {
                            AppStatisticsScreen()
                        }
                        composable(Settings.route) {
                            SettingsScreen({ u: Uri? ->
                                if (u != null) replaceDatabase(u)
                            })
                        }
                    }
                }
            }
        }
    }

    /**
     * Replaces the database with the picked file and relaunches the UI only when
     * [NeurhomeApplication.replaceDatabase] reports success. A rejected candidate leaves
     * the current database in place and shows why instead of restarting.
     */
    private fun replaceDatabase(uri: Uri) {
        Log.d(TAG, "Replacing database")
        when (val result = (application as NeurhomeApplication).replaceDatabase(uri)) {
            DatabaseReplacementResult.Replaced -> {
                Log.d(TAG, "Restarting Neurhome")
                restart()
            }
            DatabaseReplacementResult.NotADatabase -> showReplaceError(
                "That file is not a Neurhome database. The current database was kept."
            )
            is DatabaseReplacementResult.UnsupportedVersion -> showReplaceError(
                "Database version ${result.schemaVersion} is not supported by this app. " +
                    "The current database was kept."
            )
            DatabaseReplacementResult.IoError -> showReplaceError(
                "The database could not be replaced. The current database was kept."
            )
        }
    }

    private fun showReplaceError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    /**
     * Relaunches the app so the UI and its ViewModels are rebuilt over the data layer
     * rebound by [NeurhomeApplication.replaceDatabase]. The flags are required:
     * `FLAG_ACTIVITY_CLEAR_TASK` drops this instance and its ViewModels, which still hold
     * the closed database, and a `recreate()` would retain those ViewModels.
     */
    private fun restart() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }
}
