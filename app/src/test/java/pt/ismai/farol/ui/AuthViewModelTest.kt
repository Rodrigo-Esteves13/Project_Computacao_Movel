package pt.ismai.farol.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pt.ismai.farol.domain.AuthError
import pt.ismai.farol.domain.AuthException
import pt.ismai.farol.domain.AuthRepository
import pt.ismai.farol.domain.AuthSession
import pt.ismai.farol.ui.screens.auth.AuthViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private class FakeAuth(var result: Result<Unit> = Result.success(Unit)) : AuthRepository {
        var signIns = 0
        var registers = 0
        override val session: Flow<AuthSession> = flowOf(AuthSession.SignedOut)
        override suspend fun register(email: String, password: String): Result<Unit> {
            registers++
            return result
        }
        override suspend fun signIn(email: String, password: String): Result<Unit> {
            signIns++
            return result
        }
        override fun signOut() = Unit
        override suspend fun deleteAccount(): Result<Unit> = result
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun invalidInput_showsFieldErrorsAndSkipsTheCall() {
        val auth = FakeAuth()
        val vm = AuthViewModel(auth)
        vm.changeEmail("nope")
        vm.changePassword("1")
        vm.submit()
        assertNotNull(vm.ui.value.emailError)
        assertNotNull(vm.ui.value.passwordError)
        assertEquals(0, auth.signIns)
    }

    @Test fun validSignIn_callsSignInAndEndsNotBusy() {
        val auth = FakeAuth()
        val vm = AuthViewModel(auth)
        vm.changeEmail("a@b.com")
        vm.changePassword("secret123")
        vm.submit()
        assertEquals(1, auth.signIns)
        assertEquals(0, auth.registers)
        assertFalse(vm.ui.value.busy)
        assertNull(vm.ui.value.error)
    }

    @Test fun registerMode_callsRegister() {
        val auth = FakeAuth()
        val vm = AuthViewModel(auth)
        vm.toggleMode()
        vm.changeEmail("a@b.com")
        vm.changePassword("secret123")
        vm.submit()
        assertEquals(1, auth.registers)
        assertTrue(vm.ui.value.registering)
    }

    @Test fun failure_isShownAndClearedWhenTheUserTypes() {
        val auth = FakeAuth(Result.failure(AuthException(AuthError.INVALID_CREDENTIALS)))
        val vm = AuthViewModel(auth)
        vm.changeEmail("a@b.com")
        vm.changePassword("secret123")
        vm.submit()
        assertEquals(AuthError.INVALID_CREDENTIALS, vm.ui.value.error)
        vm.changePassword("other123")
        assertNull(vm.ui.value.error)
    }

    @Test fun toggleMode_keepsEmailButDropsPasswordAndErrors() {
        val vm = AuthViewModel(FakeAuth())
        vm.changeEmail("a@b.com")
        vm.changePassword("x")
        vm.toggleMode()
        assertEquals("a@b.com", vm.ui.value.email)
        assertEquals("", vm.ui.value.password)
    }
}
