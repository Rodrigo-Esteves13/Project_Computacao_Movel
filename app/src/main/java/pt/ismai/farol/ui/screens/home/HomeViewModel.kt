package pt.ismai.farol.ui.screens.home

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import pt.ismai.farol.AppContainer
import pt.ismai.farol.R
import pt.ismai.farol.domain.TargetNormalizer
import pt.ismai.farol.domain.model.AnalysisSummary

data class HomeUi(val text: String = "", @StringRes val error: Int? = null)

class HomeViewModel(repository: pt.ismai.farol.domain.AnalysisRepository) : ViewModel() {
    private val _ui = MutableStateFlow(HomeUi())
    val ui: StateFlow<HomeUi> = _ui.asStateFlow()

    val latest: StateFlow<List<AnalysisSummary>> = repository.history()
        .map { it.take(MAX_LATEST) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARING_TIMEOUT_MS), emptyList())

    fun changeText(text: String) = _ui.update { HomeUi(text) }

    /** Returns the normalized target, or null (and shows the error in the field) if invalid. */
    fun validate(): String? {
        val target = TargetNormalizer.parse(_ui.value.text)
        if (target == null) _ui.update { it.copy(error = R.string.home_error_invalid) }
        return target?.value
    }

    companion object {
        private const val MAX_LATEST = 5
        private const val SHARING_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(container.analysisRepository) }
        }
    }
}
