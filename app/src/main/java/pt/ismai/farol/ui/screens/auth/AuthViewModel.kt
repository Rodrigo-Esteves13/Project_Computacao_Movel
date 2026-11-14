package pt.ismai.farol.ui.screens.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.ismai.farol.AppContainer
import pt.ismai.farol.R
import pt.ismai.farol.domain.AuthError
import pt.ismai.farol.domain.AuthException
import pt.ismai.farol.domain.AuthRepository
import pt.ismai.farol.domain.AuthValidator

data class AuthUi(
    val email: String = "",
    val password: String = "",
    val registering: Boolean = false,
    val busy: Boolean = false,
    @StringRes val emailError: Int? = null,
    @StringRes val passwordError: Int? = null,
    val error: AuthError? = null,
)

@StringRes
fun AuthError.messageRes(): Int = when (this) {
    AuthError.EMAIL_IN_USE -> R.string.auth_error_email_in_use
    AuthError.WEAK_PASSWORD -> R.string.auth_error_weak_password
    AuthError.INVALID_CREDENTIALS -> R.string.auth_error_invalid_credentials
    AuthError.NO_INTERNET -> R.string.auth_error_no_internet
    AuthError.REQUIRES_RECENT_LOGIN -> R.string.auth_error_recent_login
    AuthError.UNKNOWN -> R.string.auth_error_unknown
}

/** One screen for both sign in and register. Success needs no callback: the session flow moves the app on. */
class AuthViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _ui = MutableStateFlow(AuthUi())
    val ui: StateFlow<AuthUi> = _ui.asStateFlow()

    fun changeEmail(text: String) = _ui.update { it.copy(email = text, emailError = null, error = null) }

    fun changePassword(text: String) = _ui.update { it.copy(password = text, passwordError = null, error = null) }

    fun toggleMode() = _ui.update { AuthUi(email = it.email, registering = !it.registering) }

    fun submit() {
        val current = _ui.value
        if (current.busy) return
        val emailError = R.string.auth_error_email_invalid.takeUnless { AuthValidator.isEmailValid(current.email) }
        val passwordError = R.string.auth_error_password_short.takeUnless { AuthValidator.isPasswordValid(current.password) }
        if (emailError != null || passwordError != null) {
            _ui.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }
        _ui.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = if (current.registering) {
                auth.register(current.email, current.password)
            } else {
                auth.signIn(current.email, current.password)
            }
            val error = result.exceptionOrNull()?.let { (it as? AuthException)?.error ?: AuthError.UNKNOWN }
            _ui.update { it.copy(busy = false, error = error) }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { AuthViewModel(container.auth) }
        }
    }
}
