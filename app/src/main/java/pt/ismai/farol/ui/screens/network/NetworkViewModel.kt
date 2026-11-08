package pt.ismai.farol.ui.screens.network

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pt.ismai.farol.AppContainer
import pt.ismai.farol.domain.AnalysisRepository
import pt.ismai.farol.domain.model.NetworkResult
import pt.ismai.farol.ui.screens.common.messageRes

sealed interface NetworkUiState {
    data object Initial : NetworkUiState
    data object Loading : NetworkUiState
    data class Success(val result: NetworkResult) : NetworkUiState
    data class Error(@StringRes val message: Int) : NetworkUiState
}

class NetworkViewModel(private val repository: AnalysisRepository) : ViewModel() {
    private val _state = MutableStateFlow<NetworkUiState>(NetworkUiState.Initial)
    val state: StateFlow<NetworkUiState> = _state.asStateFlow()

    private val errorHandler = CoroutineExceptionHandler { _, error ->
        _state.value = NetworkUiState.Error(error.messageRes())
    }

    fun check() {
        if (_state.value is NetworkUiState.Loading) return
        _state.value = NetworkUiState.Loading
        viewModelScope.launch(errorHandler) {
            _state.value = NetworkUiState.Success(repository.myNetwork())
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { NetworkViewModel(container.analysisRepository) }
        }
    }
}
