package pt.ismai.farol.data.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import pt.ismai.farol.domain.AuthError
import pt.ismai.farol.domain.AuthException
import pt.ismai.farol.domain.AuthRepository
import pt.ismai.farol.domain.AuthSession
import pt.ismai.farol.domain.AuthUser

/**
 * Maps a Firebase failure to what the UI shows. Wrong email and wrong password give the same
 * answer on purpose, so the app never reveals which accounts exist.
 */
fun authErrorFor(error: Throwable): AuthError = when (error) {
    // The weak-password exception is a subtype of the invalid-credentials one: keep it first.
    is FirebaseAuthWeakPasswordException -> AuthError.WEAK_PASSWORD
    is FirebaseAuthUserCollisionException -> AuthError.EMAIL_IN_USE
    is FirebaseAuthInvalidCredentialsException,
    is FirebaseAuthInvalidUserException,
    -> AuthError.INVALID_CREDENTIALS
    is FirebaseAuthRecentLoginRequiredException -> AuthError.REQUIRES_RECENT_LOGIN
    is FirebaseNetworkException -> AuthError.NO_INTERNET
    else -> AuthError.UNKNOWN
}

/**
 * [eraseUserData] removes what is stored under an account; it runs before the account itself is
 * deleted, because after that the security rules no longer let anyone reach the data.
 */
internal class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val eraseUserData: suspend (uid: String) -> Unit,
) : AuthRepository {

    override val session: Flow<AuthSession> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebase ->
            val user = firebase.currentUser
            trySend(
                if (user == null) AuthSession.SignedOut else AuthSession.SignedIn(AuthUser(user.uid, user.email)),
            )
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun register(email: String, password: String): Result<Unit> = call {
        auth.createUserWithEmailAndPassword(email.trim(), password).await()
    }

    override suspend fun signIn(email: String, password: String): Result<Unit> = call {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    override fun signOut() = auth.signOut()

    override suspend fun deleteAccount(): Result<Unit> = call {
        val user = auth.currentUser ?: return@call
        eraseUserData(user.uid)
        user.delete().await()
    }

    /** Like runCatching, but cancellation is not a failure and errors are already mapped. */
    private suspend fun call(block: suspend () -> Unit): Result<Unit> = try {
        block()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(AuthException(authErrorFor(e), e))
    }
}

/** Used when the build has no Firebase configuration: nobody can sign in, and nobody has to. */
internal class UnavailableAuthRepository : AuthRepository {
    override val session: Flow<AuthSession> = kotlinx.coroutines.flow.flowOf(AuthSession.Unavailable)

    override suspend fun register(email: String, password: String): Result<Unit> = unavailable()
    override suspend fun signIn(email: String, password: String): Result<Unit> = unavailable()
    override fun signOut() = Unit
    override suspend fun deleteAccount(): Result<Unit> = unavailable()

    private fun unavailable(): Result<Unit> = Result.failure(AuthException(AuthError.UNKNOWN))
}
