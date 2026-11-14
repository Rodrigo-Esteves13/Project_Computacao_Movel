package pt.ismai.farol.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import org.junit.Assert.assertEquals
import org.junit.Test
import pt.ismai.farol.data.auth.authErrorFor
import pt.ismai.farol.domain.AuthError

class AuthErrorsTest {
    @Test fun collision_isEmailInUse() {
        assertEquals(AuthError.EMAIL_IN_USE, authErrorFor(FirebaseAuthUserCollisionException("c", "m")))
    }

    @Test fun weakPassword_isNotConfusedWithInvalidCredentials() {
        assertEquals(AuthError.WEAK_PASSWORD, authErrorFor(FirebaseAuthWeakPasswordException("c", "m", "short")))
    }

    @Test fun wrongEmailOrPassword_giveTheSameAnswer() {
        val badPassword = authErrorFor(FirebaseAuthInvalidCredentialsException("c", "m"))
        val unknownUser = authErrorFor(FirebaseAuthInvalidUserException("c", "m"))
        assertEquals(AuthError.INVALID_CREDENTIALS, badPassword)
        assertEquals(badPassword, unknownUser)
    }

    @Test fun recentLogin_network_andFallback() {
        assertEquals(AuthError.REQUIRES_RECENT_LOGIN, authErrorFor(FirebaseAuthRecentLoginRequiredException("c", "m")))
        assertEquals(AuthError.NO_INTERNET, authErrorFor(FirebaseNetworkException("offline")))
        assertEquals(AuthError.UNKNOWN, authErrorFor(IllegalStateException()))
    }
}
