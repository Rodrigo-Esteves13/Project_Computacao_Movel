package pt.ismai.farol.ui.screens.history

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.ismai.farol.AppContainer
import pt.ismai.farol.domain.AnalysisRepository
import pt.ismai.farol.domain.model.AnalysisSummary

sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data class Ready(val items: List<AnalysisSummary>) : HistoryUiState
}

class HistoryViewModel(private val repository: AnalysisRepository) : ViewModel() {
    val state: StateFlow<HistoryUiState> = repository.history()
        .map<List<AnalysisSummary>, HistoryUiState> { list ->
            HistoryUiState.Ready(list.sortedByDescending { it.date })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), HistoryUiState.Loading)

    private val errorHandler = CoroutineExceptionHandler { _, error ->
        Log.e("Farol", "Error in history", error)
    }

    fun delete(id: Long) {
        viewModelScope.launch(errorHandler) { repository.deleteAnalysis(id) }
    }

    fun deleteAll() {
        viewModelScope.launch(errorHandler) { repository.clearHistory() }
    }

    companion object {
        private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { HistoryViewModel(container.analysisRepository) }
        }
    }
}
