package pt.ismai.farol.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import pt.ismai.farol.AppContainer
import pt.ismai.farol.domain.AnalysisRepository
import pt.ismai.farol.domain.AuthSession
import pt.ismai.farol.ui.screens.auth.AuthScreen
import pt.ismai.farol.ui.screens.auth.AuthViewModel
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

/**
 * The scaffold of the app: the bottom bar and, above it, the current screen. It also decides between
 * the sign-in screen and the app from the account session; draws nothing until it is known.
 */
@Composable
fun MainProgram(
    container: AppContainer,
    modifier: Modifier = Modifier,
    /** A target received from the share sheet; opened as soon as the user is past the sign-in screen. */
    sharedTarget: String? = null,
    onSharedHandled: () -> Unit = {},
) {
    val session by container.auth.session.collectAsStateWithLifecycle(AuthSession.Loading)
    if (session is AuthSession.Loading) return
    val navController = rememberNavController()
    val start = remember { if (session is AuthSession.SignedOut) Routes.AUTH else Routes.HOME }
    // Signing out (or in) replaces the whole back stack, so back never reaches the previous account's screens.
    LaunchedEffect(session) {
        val onAuth = navController.currentDestination?.route == Routes.AUTH
        when {
            session is AuthSession.SignedOut && !onAuth -> navController.leaveAccount()
            session !is AuthSession.SignedOut && onAuth -> navController.navigate(Routes.HOME) { popUpTo(0) }
        }
    }
    // Declared after the gate above, so after a sign-in the home screen is under the shared result.
    LaunchedEffect(sharedTarget, session) {
        if (sharedTarget != null && session !is AuthSession.SignedOut) {
            navController.navigate(Routes.result(sharedTarget)) { launchSingleTop = true }
            onSharedHandled()
        }
    }
    val entry by navController.currentBackStackEntryAsState()
    val showBar = Destination.toList.any { it.route == entry?.destination?.route }
    Scaffold(
        modifier = modifier,
        // Each screen has its own Scaffold, which takes care of the system bars.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { if (showBar) BottomNavigationBar(navController, appItems = Destination.toList) },
    ) { padding ->
        Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
            AppNavigation(navController, container, start)
        }
    }
}

/** One composable for each route. */
@Composable
fun AppNavigation(navController: NavHostController, container: AppContainer, startDestination: String) {
    NavHost(navController, startDestination = startDestination) {
        composable(Routes.AUTH) {
            AuthScreen(viewModel(factory = AuthViewModel.factory(container)))
        }
        home(navController, container)
        result(navController, container.analysisRepository)
        detail(navController, container)
        secondary(navController, container)
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
        )
    }
}

internal fun NavGraphBuilder.result(nav: NavHostController, repository: AnalysisRepository) {
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
        // Keyed by what is shown: opening another target over a result (a shared link) reuses this
        // back stack entry, and without the key it would keep the previous target's ViewModel.
        val vm: ResultViewModel = viewModel(
            key = "$target/${entry.analysisId()}",
            factory = ResultViewModel.factory(repository, target, entry.analysisId()),
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
