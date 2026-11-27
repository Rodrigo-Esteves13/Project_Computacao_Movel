package pt.ismai.farol.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import pt.ismai.farol.R

/**
 * The screens of the bottom bar: route, icon and title, following the multi-screen example of the
 * course. Sealed, so no other tab can be declared elsewhere.
 */
sealed class Destination(val route: String, val icon: ImageVector, @StringRes val title: Int) {
    data object Home : Destination(Routes.HOME, Icons.Filled.Home, R.string.nav_home)
    data object Favorites : Destination(Routes.FAVORITES, Icons.Filled.Star, R.string.nav_favorites)
    data object History : Destination(Routes.HISTORY, Icons.Filled.History, R.string.nav_history)
    data object Settings : Destination(Routes.SETTINGS, Icons.Filled.Settings, R.string.nav_settings)

    companion object {
        // A getter: a stored list could be built before the objects it holds.
        val toList: List<Destination> get() = listOf(Home, Favorites, History, Settings)
    }
}
