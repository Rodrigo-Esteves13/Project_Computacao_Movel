package pt.ismai.farol.ui.screens.favorites

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.ismai.farol.AppContainer
import pt.ismai.farol.R
import pt.ismai.farol.domain.AnalysisRepository
import pt.ismai.farol.domain.TargetNormalizer
import pt.ismai.farol.domain.model.FavoriteInfo
import pt.ismai.farol.ui.screens.common.messageRes

sealed interface FavoritesUiState {
    data object Loading : FavoritesUiState
    data class Ready(val items: List<FavoriteInfo>) : FavoritesUiState
}

/** "Add favorite" field. [error] != null shows the message in the field. */
data class AddFieldState(val input: String = "", @StringRes val error: Int? = null)

sealed interface RecheckState {
    data object Idle : RecheckState
    data class Running(val done: Int, val total: Int, val current: String?) : RecheckState
    data class Failed(@StringRes val message: Int) : RecheckState
}

class FavoritesViewModel(private val repository: AnalysisRepository) : ViewModel() {
    val state: StateFlow<FavoritesUiState> = repository.favorites()
        .map<List<FavoriteInfo>, FavoritesUiState> { FavoritesUiState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), FavoritesUiState.Loading)

    private val _addField = MutableStateFlow(AddFieldState())
    val addField: StateFlow<AddFieldState> = _addField.asStateFlow()

    private val _recheckState = MutableStateFlow<RecheckState>(RecheckState.Idle)
    val recheckState: StateFlow<RecheckState> = _recheckState.asStateFlow()

    private val errorHandler = CoroutineExceptionHandler { _, error ->
        _addField.update { it.copy(error = error.messageRes()) }
    }
    private val recheckHandler = CoroutineExceptionHandler { _, error ->
        _recheckState.value = RecheckState.Failed(error.messageRes())
    }

    fun changeInput(text: String) {
        _addField.value = AddFieldState(input = text)
    }

    fun add() {
        val target = TargetNormalizer.parse(_addField.value.input)
        if (target == null) {
            _addField.update { it.copy(error = R.string.favorites_error_invalid) }
            return
        }
        viewModelScope.launch(errorHandler) {
            if (!repository.isFavorite(target.value).first()) repository.toggleFavorite(target.value)
            _addField.value = AddFieldState()
        }
    }

    fun remove(target: String) {
        viewModelScope.launch(errorHandler) { repository.toggleFavorite(target) }
    }

    fun recheck() {
        if (_recheckState.value is RecheckState.Running) return
        val total = (state.value as? FavoritesUiState.Ready)?.items?.size ?: 0
        _recheckState.value = RecheckState.Running(done = 0, total = total, current = null)
        viewModelScope.launch(recheckHandler) {
            var done = 0
            repository.recheckFavorites().collect { target ->
                done++
                _recheckState.value = RecheckState.Running(done, total, target)
            }
            _recheckState.value = RecheckState.Idle
        }
    }

    companion object {
        private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { FavoritesViewModel(container.analysisRepository) }
        }
    }
}
