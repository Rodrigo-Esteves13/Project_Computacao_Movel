package pt.ismai.farol.ui.screens.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.ismai.farol.AppContainer
import pt.ismai.farol.domain.AuthError
import pt.ismai.farol.domain.AuthException
import pt.ismai.farol.domain.AuthRepository
import pt.ismai.farol.domain.AuthSession
import pt.ismai.farol.domain.SettingsRepository
import pt.ismai.farol.domain.model.ThemePref

sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    /** [session] decides whether the account section is shown. */
    data class Ready(
        val theme: ThemePref,
        val timeoutSeconds: Int,
        val session: AuthSession,
    ) : SettingsUiState
}

/** Timeout options offered to the user, in seconds. */
val TIMEOUT_OPTIONS_SECONDS = listOf(5, 10, 20, 30)

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    val state: StateFlow<SettingsUiState> =
        combine(settings.theme, settings.timeoutSeconds, auth.session) { theme, timeout, session ->
            SettingsUiState.Ready(theme, timeout, session) as SettingsUiState
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), SettingsUiState.Loading)

    private val errorHandler = CoroutineExceptionHandler { _, error ->
        Log.e("Farol", "Error in settings", error)
    }

    private val _accountError = MutableStateFlow<AuthError?>(null)
    val accountError: StateFlow<AuthError?> = _accountError.asStateFlow()

    fun signOut() = auth.signOut()

    fun deleteAccount() {
        viewModelScope.launch(errorHandler) {
            val failure = auth.deleteAccount().exceptionOrNull()
            _accountError.value = failure?.let { (it as? AuthException)?.error ?: AuthError.UNKNOWN }
        }
    }

    fun dismissAccountError() {
        _accountError.value = null
    }

    fun chooseTheme(theme: ThemePref) {
        viewModelScope.launch(errorHandler) { settings.setTheme(theme) }
    }

    fun chooseTimeout(seconds: Int) {
        viewModelScope.launch(errorHandler) { settings.setTimeout(seconds) }
    }

    companion object {
        private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { SettingsViewModel(container.settings, container.auth) }
        }
    }
}
