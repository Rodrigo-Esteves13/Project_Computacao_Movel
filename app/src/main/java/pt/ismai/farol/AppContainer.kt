package pt.ismai.farol

import android.content.Context
import pt.ismai.farol.data.fake.FakeAnalysisRepository
import pt.ismai.farol.data.fake.FakeSettingsRepository
import pt.ismai.farol.domain.AnalysisRepository
import pt.ismai.farol.domain.SettingsRepository

/**
 * Manual dependency injection. The property names are the contract with the UI.
 * Phase 2 wires in-memory fakes; the real implementations replace them in later phases.
 */
class AppContainer(context: Context) {
    val analysisRepository: AnalysisRepository by lazy { FakeAnalysisRepository() }
    val settings: SettingsRepository by lazy { FakeSettingsRepository() }
}
