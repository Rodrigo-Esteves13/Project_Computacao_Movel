package pt.ismai.farol.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import pt.ismai.farol.AppContainer
import pt.ismai.farol.ui.screens.settings.SettingsScreen
import pt.ismai.farol.ui.screens.detail.DetailScreen
import pt.ismai.farol.ui.screens.detail.DetailViewModel
import pt.ismai.farol.ui.screens.favorites.FavoritesScreen
import pt.ismai.farol.ui.screens.history.HistoryScreen
import pt.ismai.farol.ui.screens.home.HomeScreen
import pt.ismai.farol.ui.screens.home.HomeViewModel
import pt.ismai.farol.ui.screens.network.NetworkScreen
import pt.ismai.farol.ui.screens.result.ResultScreen
import pt.ismai.farol.ui.screens.result.ResultViewModel

@Composable
fun FarolNavHost(container: AppContainer, modifier: Modifier = Modifier) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = Routes.HOME, modifier = modifier) {
        home(nav, container)
        result(nav, container)
        detail(nav, container)
        secondary(nav, container)
    }
}

private fun NavGraphBuilder.home(nav: NavHostController, container: AppContainer) {
    composable(Routes.HOME) { entry ->
        val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
        HomeScreen(
            viewModel = vm,
            onAnalyze = { nav.goTo(entry, Routes.result(it)) },
            onOpenAnalysis = { id, target -> nav.goTo(entry, Routes.result(target, id)) },
            onNetwork = { nav.goTo(entry, Routes.NETWORK) },
            onFavorites = { nav.goTo(entry, Routes.FAVORITES) },
            onHistory = { nav.goTo(entry, Routes.HISTORY) },
            onSettings = { nav.goTo(entry, Routes.SETTINGS) },
        )
    }
}

private fun NavGraphBuilder.result(nav: NavHostController, container: AppContainer) {
    composable(
        Routes.RESULT,
        arguments = listOf(
            navArgument(Routes.ARG_TARGET) { type = NavType.StringType },
            navArgument(Routes.ARG_ANALYSIS_ID) {
                type = NavType.LongType
                defaultValue = Routes.NO_ANALYSIS
            },
        ),
    ) { entry ->
        val target = entry.arguments?.getString(Routes.ARG_TARGET).orEmpty()
        val vm: ResultViewModel = viewModel(
            factory = ResultViewModel.factory(container, target, entry.analysisId()),
        )
        ResultScreen(
            viewModel = vm,
            target = target,
            onBack = { nav.goBack(entry) },
            onDetail = { id, check -> nav.goTo(entry, Routes.detail(id, check)) },
        )
    }
}

private fun NavGraphBuilder.detail(nav: NavHostController, container: AppContainer) {
    composable(
        Routes.DETAIL,
        arguments = listOf(
            navArgument(Routes.ARG_ANALYSIS_ID) { type = NavType.LongType },
            navArgument(Routes.ARG_CHECK) { type = NavType.StringType },
        ),
    ) { entry ->
        val vm: DetailViewModel = viewModel(
            factory = DetailViewModel.factory(container, entry.analysisId(), entry.check()),
        )
        DetailScreen(viewModel = vm, onBack = { nav.goBack(entry) })
    }
}

private fun NavGraphBuilder.secondary(nav: NavHostController, container: AppContainer) {
    composable(Routes.NETWORK) { entry -> NetworkScreen(container, onBack = { nav.goBack(entry) }) }
    composable(Routes.FAVORITES) { entry ->
        FavoritesScreen(
            container,
            onBack = { nav.goBack(entry) },
            onOpen = { nav.goTo(entry, Routes.result(it)) },
        )
    }
    composable(Routes.HISTORY) { entry ->
        HistoryScreen(
            container,
            onBack = { nav.goBack(entry) },
            onOpenAnalysis = { id, target -> nav.goTo(entry, Routes.result(target, id)) },
        )
    }
    composable(Routes.SETTINGS) { entry -> SettingsScreen(container, onBack = { nav.goBack(entry) }) }
}

private fun NavBackStackEntry.analysisId(): Long =
    arguments?.getLong(Routes.ARG_ANALYSIS_ID) ?: Routes.NO_ANALYSIS

private fun NavBackStackEntry.check(): String =
    arguments?.getString(Routes.ARG_CHECK).orEmpty()

/** Only navigates if the source screen is visible: ignores repeated taps during the transition. */
private fun NavHostController.goTo(origin: NavBackStackEntry, route: String) {
    if (origin.lifecycle.currentState == Lifecycle.State.RESUMED) {
        navigate(route) { launchSingleTop = true }
    }
}

private fun NavHostController.goBack(origin: NavBackStackEntry) {
    if (origin.lifecycle.currentState == Lifecycle.State.RESUMED) popBackStack()
}
