package pt.ismai.farol.ui.screens.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.ismai.farol.AppContainer
import pt.ismai.farol.domain.SettingsRepository
import pt.ismai.farol.domain.model.ThemePref

sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    data class Ready(val theme: ThemePref, val timeoutSeconds: Int) : SettingsUiState
}

/** Timeout options offered to the user, in seconds. */
val TIMEOUT_OPTIONS_SECONDS = listOf(5, 10, 20, 30)

class SettingsViewModel(private val settings: SettingsRepository) : ViewModel() {
    val state: StateFlow<SettingsUiState> =
        combine(settings.theme, settings.timeoutSeconds) { theme, timeout ->
            SettingsUiState.Ready(theme, timeout) as SettingsUiState
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), SettingsUiState.Loading)

    private val errorHandler = CoroutineExceptionHandler { _, error ->
        Log.e("Farol", "Error in settings", error)
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
            initializer { SettingsViewModel(container.settings) }
        }
    }
}
