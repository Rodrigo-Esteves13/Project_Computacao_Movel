package pt.ismai.farol.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pt.ismai.farol.ui.navigation.BottomNavigationBar
import pt.ismai.farol.ui.navigation.Destination
import pt.ismai.farol.ui.navigation.Routes
import pt.ismai.farol.ui.navigation.leaveAccount
import pt.ismai.farol.ui.navigation.switchTab
import pt.ismai.farol.ui.theme.FarolTheme

/** The bottom bar and the back stack rules, over a graph of placeholder screens. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NavigationTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var nav: NavHostController

    private fun start() {
        compose.setContent {
            FarolTheme {
                nav = rememberNavController()
                androidx.compose.foundation.layout.Column {
                    NavHost(nav, startDestination = Routes.HOME) {
                        listOf(Routes.AUTH, Routes.HOME, Routes.FAVORITES, Routes.HISTORY, Routes.SETTINGS, "inside")
                            .forEach { route -> composable(route) { Text("screen $route") } }
                    }
                    BottomNavigationBar(nav, Destination.toList)
                }
            }
        }
    }

    private fun tab(title: String) = compose.onNode(hasText(title) and hasClickAction())

    private fun route() = nav.currentDestination?.route

    private fun run(block: () -> Unit) {
        compose.runOnIdle(block)
        compose.waitForIdle()
    }

    @Test fun barShowsTheFourTabs_andClickingSwitchesScreen() {
        start()
        listOf("Home", "Favorites", "History", "Settings").forEach { tab(it).assertIsDisplayed() }
        tab("History").performClick()
        compose.onNodeWithText("screen history").assertIsDisplayed()
        tab("History").assertIsSelected()
    }

    @Test fun aTabKeepsWhatItWasShowing() {
        start()
        tab("History").performClick()
        run { nav.navigate("inside") }
        tab("Home").performClick()
        compose.waitForIdle()
        assertEquals(Routes.HOME, route())
        tab("History").performClick()
        compose.waitForIdle()
        assertEquals("inside", route())
    }

    @Test fun switchingTabsDoesNotStackScreens() {
        start()
        run {
            nav.switchTab(Routes.HISTORY)
            nav.switchTab(Routes.SETTINGS)
            nav.switchTab(Routes.FAVORITES)
        }
        run { nav.popBackStack() }
        assertEquals(Routes.HOME, route())
    }

    @Test fun afterLeavingTheAccount_noSavedScreenComesBack() {
        start()
        tab("History").performClick()
        run { nav.navigate("inside") }
        tab("Home").performClick()
        run { nav.leaveAccount() }
        assertEquals(Routes.AUTH, route())
        // The next account signs in and opens History: it must not land on the previous one's screen.
        run { nav.navigate(Routes.HOME) { popUpTo(0) } }
        tab("History").performClick()
        compose.waitForIdle()
        assertEquals(Routes.HISTORY, route())
    }
}
