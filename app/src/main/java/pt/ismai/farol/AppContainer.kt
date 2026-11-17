package pt.ismai.farol

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import pt.ismai.farol.data.auth.FirebaseAuthRepository
import pt.ismai.farol.data.auth.UnavailableAuthRepository
import pt.ismai.farol.data.firestore.FirestoreAnalysisStore
import pt.ismai.farol.data.firestore.FirestoreRemoteSettings
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
import pt.ismai.farol.data.repository.AndroidDataTexts
import pt.ismai.farol.data.repository.AnalysisStore
import pt.ismai.farol.data.repository.InMemoryAnalysisStore
import pt.ismai.farol.data.repository.SettingsRepositoryImpl
import pt.ismai.farol.data.repository.farolDataStore
import pt.ismai.farol.domain.AnalysisRepository
import pt.ismai.farol.domain.AuthRepository
import pt.ismai.farol.domain.AuthSession
import pt.ismai.farol.domain.SettingsRepository
import pt.ismai.farol.util.Connectivity
import pt.ismai.farol.work.DailyCheckScheduler

/** Manual injection. The property names are the contract with the UI. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val timeout = ConfigurableTimeout()

    /** Theme and timeout are kept on the device and, with an account, in Firestore too. */
    val settings: SettingsRepository by lazy {
        val remote = if (firebaseConfigured) FirestoreRemoteSettings(FirebaseFirestore.getInstance(), signedInUid) else null
        SettingsRepositoryImpl(appContext.farolDataStore, remote).also { repository ->
            if (remote != null) scope.launch { repository.sync() }
        }
    }

    val dailyCheck: DailyCheckScheduler by lazy { DailyCheckScheduler(appContext) }

    private val firebaseConfigured: Boolean get() = FirebaseApp.getApps(appContext).isNotEmpty()

    /** Accounts need app/google-services.json; without it the app runs with no sign-in gate. */
    val auth: AuthRepository by lazy {
        if (firebaseConfigured) {
            FirebaseAuthRepository(FirebaseAuth.getInstance()) { uid -> firestoreStore.deleteAllFor(uid) }
        } else {
            UnavailableAuthRepository()
        }
    }

    /** The signed-in account's uid, or null; shared by everything stored per account. */
    private val signedInUid by lazy {
        auth.session.map { (it as? AuthSession.SignedIn)?.user?.uid }.distinctUntilChanged()
    }

    private val firestoreStore: FirestoreAnalysisStore by lazy {
        FirestoreAnalysisStore(FirebaseFirestore.getInstance(), signedInUid)
    }

    /** History and favorites: in the signed-in account's Firestore branch, or in memory without Firebase. */
    private val store: AnalysisStore by lazy {
        if (firebaseConfigured) {
            firestoreStore
        } else {
            InMemoryAnalysisStore()
        }
    }

    private val okHttp by lazy {
        // Load the saved timeout before the first request, then follow later changes.
        timeout.seconds = runBlocking(Dispatchers.IO) { settings.timeoutSeconds.first() }
        scope.launch { settings.timeoutSeconds.collect { timeout.seconds = it } }
        createOkHttp(timeout)
    }

    val analysisRepository: AnalysisRepository by lazy {
        val texts = AndroidDataTexts(appContext)
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
            store = store,
        )
    }
}
