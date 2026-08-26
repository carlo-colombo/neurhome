package ovh.litapp.neurhome3

import androidx.navigation.NavController

object Navigator {
    sealed interface Destination {
        val route: String

        data object Home : Destination {
            override val route = "home"
        }

        data object ApplicationList : Destination {
            override val route = "applicationList"
        }

        data object Categories : Destination {
            override val route = "categories"
        }

        data object Settings : Destination {
            override val route = "settings"
        }

        data object AppStatistics : Destination {
            override val route = "appStatistics"
        }
    }

    val destinations = listOf(
        Destination.Home,
        Destination.ApplicationList,
        Destination.Categories,
        Destination.Settings,
        Destination.AppStatistics,
    )
}

fun NavController.navigateTo(destination: Navigator.Destination) {
    navigate(destination.route) {
        launchSingleTop = true
    }
}
