package pt.ismai.farol.data.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import pt.ismai.farol.domain.SettingsRepository
import pt.ismai.farol.domain.model.ThemePref

/** Phase 2 stand-in: settings live in memory only. Replaced by the DataStore version later. */
class FakeSettingsRepository : SettingsRepository {
    private val themeState = MutableStateFlow(ThemePref.SYSTEM)
    private val timeoutState = MutableStateFlow(DEFAULT_TIMEOUT_SECONDS)

    override val theme: Flow<ThemePref> = themeState
    override val timeoutSeconds: Flow<Int> = timeoutState

    override suspend fun setTheme(theme: ThemePref) {
        themeState.value = theme
    }

    override suspend fun setTimeout(seconds: Int) {
        timeoutState.value = seconds
    }

    private companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 10
    }
}
