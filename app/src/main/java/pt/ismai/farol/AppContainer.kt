package pt.ismai.farol

import android.content.Context
import pt.ismai.farol.data.fake.FakeSettingsRepository
import pt.ismai.farol.data.remote.ConfigurableTimeout
import pt.ismai.farol.data.remote.DnsApi
import pt.ismai.farol.data.remote.createApi
import pt.ismai.farol.data.remote.createOkHttp
import pt.ismai.farol.data.repository.AnalysisRepositoryImpl
import pt.ismai.farol.data.repository.Checkers
import pt.ismai.farol.data.repository.DataTexts
import pt.ismai.farol.data.repository.InMemoryAnalysisStore
import pt.ismai.farol.domain.AnalysisRepository
import pt.ismai.farol.domain.SettingsRepository
import pt.ismai.farol.util.Connectivity

/** Manual injection. The property names are the contract with the UI. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    // Stand-in until the DataStore settings arrive.
    val settings: SettingsRepository by lazy { FakeSettingsRepository() }

    private val okHttp by lazy { createOkHttp(ConfigurableTimeout()) }

    val analysisRepository: AnalysisRepository by lazy {
        val texts = DataTexts(appContext)
        AnalysisRepositoryImpl(
            checkers = Checkers(dns = createApi<DnsApi>(okHttp, DnsApi.BASE_URL), texts = texts),
            connectivity = Connectivity(appContext),
            texts = texts,
            store = InMemoryAnalysisStore(),
        )
    }
}
