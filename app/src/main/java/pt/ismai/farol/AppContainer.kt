package pt.ismai.farol

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import pt.ismai.farol.data.remote.ConfigurableTimeout
import pt.ismai.farol.data.remote.DnsApi
import pt.ismai.farol.data.remote.HttpChecker
import pt.ismai.farol.data.remote.InternetDbApi
import pt.ismai.farol.data.remote.IpifyApi
import pt.ismai.farol.data.remote.RdapApi
import pt.ismai.farol.data.remote.createApi
import pt.ismai.farol.data.remote.createOkHttp
import pt.ismai.farol.data.repository.AnalysisRepositoryImpl
import pt.ismai.farol.data.repository.Checkers
import pt.ismai.farol.data.repository.DataTexts
import pt.ismai.farol.data.repository.InMemoryAnalysisStore
import pt.ismai.farol.data.repository.SettingsRepositoryImpl
import pt.ismai.farol.data.repository.farolDataStore
import pt.ismai.farol.domain.AnalysisRepository
import pt.ismai.farol.domain.SettingsRepository
import pt.ismai.farol.util.Connectivity

/** Manual injection. The property names are the contract with the UI. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val timeout = ConfigurableTimeout()

    val settings: SettingsRepository by lazy { SettingsRepositoryImpl(appContext.farolDataStore) }

    private val okHttp by lazy {
        // Load the saved timeout before the first request, then follow later changes.
        timeout.seconds = runBlocking(Dispatchers.IO) { settings.timeoutSeconds.first() }
        scope.launch { settings.timeoutSeconds.collect { timeout.seconds = it } }
        createOkHttp(timeout)
    }

    val analysisRepository: AnalysisRepository by lazy {
        val texts = DataTexts(appContext)
        AnalysisRepositoryImpl(
            checkers = Checkers(
                dns = createApi<DnsApi>(okHttp, DnsApi.BASE_URL),
                internetDb = createApi<InternetDbApi>(okHttp, InternetDbApi.BASE_URL),
                ipify = createApi<IpifyApi>(okHttp, IpifyApi.BASE_URL),
                rdap = createApi<RdapApi>(okHttp, RdapApi.BASE_URL),
                http = HttpChecker(okHttp),
                texts = texts,
            ),
            connectivity = Connectivity(appContext),
            texts = texts,
            store = InMemoryAnalysisStore(),
        )
    }
}
