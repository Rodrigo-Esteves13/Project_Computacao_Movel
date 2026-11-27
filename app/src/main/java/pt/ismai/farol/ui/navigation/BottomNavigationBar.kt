package pt.ismai.farol.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

private const val INDICATOR_ALPHA = 0.16f

/**
 * Bar at the bottom of the screen that switches between the [appItems]. Same structure as the course
 * example, with the Material 3 bar so the whole app uses one design library.
 */
@Composable
fun BottomNavigationBar(navController: NavController, appItems: List<Destination>) {
    // The theme does not define the Material 3 container colours, so they are set from its own.
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = INDICATOR_ALPHA),
    )
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        appItems.forEach { item ->
            NavigationBarItem(
                // The label below already names the item, so the icon is silent for TalkBack.
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(stringResource(item.title)) },
                selected = currentRoute == item.route,
                onClick = { navController.switchTab(item.route) },
                colors = colors,
            )
        }
    }
}

/**
 * Opens a bottom bar screen without stacking copies, keeping what each tab was showing. The course
 * example pops to the start destination; here it is the home screen, because the app may start on
 * the sign-in screen, which is no longer in the stack after signing in.
 */
fun NavController.switchTab(route: String) {
    navigate(route) {
        popUpTo(Destination.Home.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Goes to the sign-in screen and forgets everything: the back stack and also what each tab saved,
 * so the next account can never get a screen of the previous one back.
 */
fun NavController.leaveAccount() {
    navigate(Routes.AUTH) { popUpTo(0) }
    Destination.toList.forEach { clearBackStack(it.route) }
}
