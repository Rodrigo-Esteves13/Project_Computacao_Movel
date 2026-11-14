package pt.ismai.farol.domain

import kotlinx.coroutines.flow.Flow

data class AuthUser(val uid: String, val email: String?)

/** What the navigation gate needs to know about the account. */
sealed interface AuthSession {
    /** The first answer has not arrived yet. */
    data object Loading : AuthSession

    /** This build has no Firebase configuration: the app runs without accounts. */
    data object Unavailable : AuthSession

    data object SignedOut : AuthSession
    data class SignedIn(val user: AuthUser) : AuthSession
}

enum class AuthError {
    EMAIL_IN_USE,
    WEAK_PASSWORD,
    INVALID_CREDENTIALS,
    NO_INTERNET,
    REQUIRES_RECENT_LOGIN,
    UNKNOWN,
}

/** Failure of an account operation; [error] is what the UI turns into a message. */
class AuthException(val error: AuthError, cause: Throwable? = null) : Exception(error.name, cause)

/** Contract between UI and the account provider. Implementation: data/auth. */
interface AuthRepository {
    val session: Flow<AuthSession>

    /** Failures arrive as [AuthException]. */
    suspend fun register(email: String, password: String): Result<Unit>
    suspend fun signIn(email: String, password: String): Result<Unit>
    fun signOut()

    /** Deletes the account and everything stored under it. */
    suspend fun deleteAccount(): Result<Unit>
}

/** Local checks done before any request is made. */
object AuthValidator {
    const val MIN_PASSWORD_LENGTH = 6
    private val EMAIL = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")

    fun isEmailValid(email: String): Boolean = EMAIL.matches(email.trim())

    fun isPasswordValid(password: String): Boolean = password.length >= MIN_PASSWORD_LENGTH
}
