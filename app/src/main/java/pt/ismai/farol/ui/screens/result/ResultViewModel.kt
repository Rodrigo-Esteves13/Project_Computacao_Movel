package pt.ismai.farol.ui.screens.result

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.ismai.farol.AppContainer
import pt.ismai.farol.R
import pt.ismai.farol.domain.AnalysisRepository
import pt.ismai.farol.domain.model.AnalysisProgress
import pt.ismai.farol.domain.model.AnalysisResult
import pt.ismai.farol.domain.reportText
import pt.ismai.farol.ui.screens.common.messageRes

sealed interface ResultUiState {
    data object Loading : ResultUiState
    data class Success(val progress: AnalysisProgress) : ResultUiState
    data class Error(@StringRes val message: Int) : ResultUiState
}

class ResultViewModel(
    private val repository: AnalysisRepository,
    private val target: String,
    private val initialAnalysisId: Long,
) : ViewModel() {
    private val _state = MutableStateFlow<ResultUiState>(ResultUiState.Loading)
    val state: StateFlow<ResultUiState> = _state.asStateFlow()

    val isFavorite: StateFlow<Boolean> = repository.isFavorite(target)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARING_TIMEOUT_MS), false)

    private val handler = CoroutineExceptionHandler { _, error ->
        Log.e(TAG, "Unhandled error", error)
        _state.value = ResultUiState.Error(R.string.error_generic)
    }
    private var job: Job? = null
    private var analysisDate = System.currentTimeMillis()

    init {
        retry()
    }

    fun retry() {
        if (initialAnalysisId >= 0) loadSaved() else analyze(force = false)
    }

    fun reanalyze() = analyze(force = true)

    fun toggleFavorite() {
        viewModelScope.launch(handler) { repository.toggleFavorite(target) }
    }

    /** Report text to share, or null if there is no result yet. */
    fun report(): String? {
        val progress = (_state.value as? ResultUiState.Success)?.progress?.takeIf { it.finished } ?: return null
        return reportText(
            AnalysisResult(target, progress.type, progress.completed, progress.score, analysisDate, progress.analysisId ?: 0),
        )
    }

    private fun analyze(force: Boolean) {
        job?.cancel()
        _state.value = ResultUiState.Loading
        job = viewModelScope.launch(handler) {
            repository.analyze(target, force)
                .catch { _state.value = ResultUiState.Error(it.messageRes()) }
                .collect { progress ->
                    if (progress.finished) analysisDate = analysisDateOf(progress)
                    _state.value = ResultUiState.Success(progress)
                }
        }
    }

    /** Analyses served from the cache keep the date they were performed. */
    private suspend fun analysisDateOf(p: AnalysisProgress): Long {
        val saved = if (p.fromCache) p.analysisId?.let { repository.getAnalysis(it) } else null
        return saved?.date ?: System.currentTimeMillis()
    }

    private fun loadSaved() {
        job?.cancel()
        _state.value = ResultUiState.Loading
        job = viewModelScope.launch(handler) {
            val saved = repository.getAnalysis(initialAnalysisId)
            _state.value = if (saved == null) {
                ResultUiState.Error(R.string.result_not_found)
            } else {
                analysisDate = saved.date
                ResultUiState.Success(saved.asProgress())
            }
        }
    }

    companion object {
        private const val TAG = "Farol"
        private const val SHARING_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer, target: String, analysisId: Long): ViewModelProvider.Factory =
            viewModelFactory { initializer { ResultViewModel(container.analysisRepository, target, analysisId) } }
    }
}

private fun AnalysisResult.asProgress() = AnalysisProgress(
    target = target,
    type = type,
    completed = checks,
    pending = emptyList(),
    score = score,
    finished = true,
    fromCache = false,
    analysisId = id,
)
