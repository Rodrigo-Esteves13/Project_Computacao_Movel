package pt.ismai.farol.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pt.ismai.farol.domain.AnalysisRepository
import pt.ismai.farol.domain.model.AnalysisProgress
import pt.ismai.farol.domain.model.AnalysisResult
import pt.ismai.farol.domain.model.AnalysisSummary
import pt.ismai.farol.domain.model.Check
import pt.ismai.farol.domain.model.CheckState
import pt.ismai.farol.domain.model.FavoriteInfo
import pt.ismai.farol.domain.model.NetworkResult
import pt.ismai.farol.domain.model.TargetType
import pt.ismai.farol.ui.navigation.Routes
import pt.ismai.farol.ui.navigation.result
import pt.ismai.farol.ui.theme.FarolTheme

/** Found on a device: sharing a second link while a result was open showed the first result. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ResultRouteTest {
    @get:Rule val compose = createComposeRule()

    /** Each analysis names its own target, so the screen shows whose data it holds. */
    private class NamingRepository : AnalysisRepository {
        override fun analyze(input: String, force: Boolean): Flow<AnalysisProgress> {
            val check = Check("exposure", "Port exposure", CheckState.OK, "data of $input", "", 15, 15)
            return flowOf(AnalysisProgress(input, TargetType.DOMAIN, listOf(check), emptyList(), 100, true, false, 1L))
        }
        override suspend fun getAnalysis(id: Long): AnalysisResult? = null
        override fun history(): Flow<List<AnalysisSummary>> = flowOf(emptyList())
        override suspend fun deleteAnalysis(id: Long) = Unit
        override suspend fun clearHistory() = Unit
        override fun favorites(): Flow<List<FavoriteInfo>> = flowOf(emptyList())
        override fun isFavorite(target: String): Flow<Boolean> = flowOf(false)
        override suspend fun toggleFavorite(target: String) = Unit
        override fun recheckFavorites(): Flow<String> = flowOf()
        override suspend fun myNetwork(): NetworkResult = error("not used")
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun aSecondTargetOpenedOverAResult_showsItsOwnData() {
        lateinit var nav: NavHostController
        val repository = NamingRepository()
        compose.setContent {
            FarolTheme {
                nav = rememberNavController()
                NavHost(nav, startDestination = Routes.HOME) {
                    composable(Routes.HOME) { Text("home") }
                    result(nav, repository)
                }
            }
        }
        // The same call the app makes when a link is shared to it.
        fun share(target: String) {
            compose.runOnIdle { nav.navigate(Routes.result(target)) { launchSingleTop = true } }
            compose.waitForIdle()
        }
        share("first.example")
        compose.onNodeWithText("data of first.example").assertIsDisplayed()
        share("second.example")
        compose.onNodeWithText("data of second.example").assertIsDisplayed()
    }
}
