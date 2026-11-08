package pt.ismai.farol.ui.screens.detail

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pt.ismai.farol.AppContainer
import pt.ismai.farol.R
import pt.ismai.farol.domain.AnalysisRepository
import pt.ismai.farol.domain.model.Check

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Success(val check: Check) : DetailUiState
    data class Error(@StringRes val message: Int) : DetailUiState
}

class DetailViewModel(
    private val repository: AnalysisRepository,
    private val analysisId: Long,
    private val checkId: String,
) : ViewModel() {
    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private val handler = CoroutineExceptionHandler { _, error ->
        Log.e(TAG, "Unhandled error", error)
        _state.value = DetailUiState.Error(R.string.error_generic)
    }

    init {
        viewModelScope.launch(handler) {
            val check = repository.getAnalysis(analysisId)?.checks?.firstOrNull { it.id == checkId }
            _state.value = if (check == null) {
                DetailUiState.Error(R.string.detail_not_found)
            } else {
                DetailUiState.Success(check)
            }
        }
    }

    companion object {
        private const val TAG = "Farol"

        fun factory(container: AppContainer, analysisId: Long, checkId: String): ViewModelProvider.Factory =
            viewModelFactory { initializer { DetailViewModel(container.analysisRepository, analysisId, checkId) } }
    }
}
