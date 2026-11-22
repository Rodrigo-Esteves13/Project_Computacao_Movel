package pt.ismai.farol.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pt.ismai.farol.domain.AuthRepository
import pt.ismai.farol.domain.AuthSession
import pt.ismai.farol.domain.model.Check
import pt.ismai.farol.domain.model.CheckState
import pt.ismai.farol.ui.components.CheckCard
import pt.ismai.farol.ui.components.ScoreGauge
import pt.ismai.farol.ui.screens.auth.AuthScreen
import pt.ismai.farol.ui.screens.auth.AuthViewModel
import pt.ismai.farol.ui.theme.FarolTheme

/** Compose screens drawn on the JVM by Robolectric: no emulator needed, so CI can run them too. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScreensTest {
    @get:Rule val compose = createComposeRule()

    private class FakeAuth : AuthRepository {
        var signIns = 0
        var lastEmail = ""
        override val session: Flow<AuthSession> = flowOf(AuthSession.SignedOut)
        override suspend fun register(email: String, password: String): Result<Unit> = Result.success(Unit)
        override suspend fun signIn(email: String, password: String): Result<Unit> {
            signIns++
            lastEmail = email
            return Result.success(Unit)
        }
        override fun signOut() = Unit
        override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun signInButton() = hasText("Sign in") and hasClickAction()

    @Test fun authScreen_emptyForm_showsBothFieldErrors() {
        val auth = FakeAuth()
        compose.setContent { FarolTheme { AuthScreen(AuthViewModel(auth)) } }
        compose.onNode(signInButton()).performClick()
        compose.onNodeWithText("Enter a valid email address.").assertIsDisplayed()
        compose.onNodeWithText("The password needs at least 6 characters.").assertIsDisplayed()
        assertEquals(0, auth.signIns)
    }

    @Test fun authScreen_validForm_signsIn() {
        val auth = FakeAuth()
        compose.setContent { FarolTheme { AuthScreen(AuthViewModel(auth)) } }
        compose.onNodeWithText("Email").performTextInput("a@b.com")
        compose.onNodeWithText("Password").performTextInput("secret123")
        compose.onNode(signInButton()).performClick()
        compose.waitForIdle()
        assertEquals(1, auth.signIns)
        assertEquals("a@b.com", auth.lastEmail)
    }

    @Test fun authScreen_switchToRegister_changesTheTitle() {
        compose.setContent { FarolTheme { AuthScreen(AuthViewModel(FakeAuth())) } }
        compose.onNodeWithText("No account yet? Create one").performClick()
        compose.onNodeWithText("Already have an account? Sign in").assertIsDisplayed()
        assertTrue(compose.onAllCreateAccountNodes() >= 2) // title and button
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllCreateAccountNodes(): Int =
        onAllNodes(hasText("Create account")).fetchSemanticsNodes().size

    @Test fun scoreGauge_showsTheScoreAndItsState() {
        compose.setContent { FarolTheme { ScoreGauge(85) } }
        compose.onNodeWithText("85").assertIsDisplayed()
        compose.onNodeWithText("Good").assertIsDisplayed()
    }

    @Test fun scoreGauge_withoutScoreWhileAnalyzing_showsAnalyzing() {
        compose.setContent { FarolTheme { ScoreGauge(null, analyzing = true) } }
        compose.onNodeWithText("Analyzing", substring = true).assertIsDisplayed()
    }

    @Test fun checkCard_showsTitleAndSummary_andReportsTheClick() {
        var clicks = 0
        val check = Check("certificate", "SSL certificate", CheckState.OK, "Valid, expires in 90 days", "", 25, 25)
        compose.setContent { FarolTheme { CheckCard(check, onClick = { clicks++ }) } }
        compose.onNodeWithText("SSL certificate").assertIsDisplayed()
        compose.onNodeWithText("Valid, expires in 90 days").performClick()
        assertEquals(1, clicks)
    }

    @Test fun checkCard_isOneTalkBackStop_withTheTitleAsHeading() {
        val check = Check("certificate", "SSL certificate", CheckState.OK, "Valid, expires in 90 days", "", 25, 25)
        compose.setContent { FarolTheme { CheckCard(check, onClick = null) } }
        // Merged: state icon, title and summary are read as a single node.
        compose.onAllNodes(hasText("Valid, expires in 90 days")).assertCountEquals(1)
        compose.onNode(hasText("SSL certificate") and hasText("Valid, expires in 90 days")).assertIsDisplayed()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading), useUnmergedTree = true)
            .assertTextContains("SSL certificate")
    }
}
