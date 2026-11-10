package pt.ismai.farol.data.remote

import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private const val USER_AGENT = "Farol/1.0 (ISMAI academic project)"
private const val TIMEOUT_DEFAULT_S = 10
private const val CALL_LIMIT_S = 60L

/** Timeout (seconds) read by the client on every request; updated from the settings. */
class ConfigurableTimeout(@Volatile var seconds: Int = TIMEOUT_DEFAULT_S)

/** The app's single OkHttpClient (Retrofit + HttpChecker). */
fun createOkHttp(timeout: ConfigurableTimeout): OkHttpClient {
    val builder = OkHttpClient.Builder()
        .callTimeout(CALL_LIMIT_S, TimeUnit.SECONDS)
        .addInterceptor(Interceptor { chain ->
            val s = timeout.seconds
            val request = chain.request().newBuilder().header("User-Agent", USER_AGENT).build()
            chain.withConnectTimeout(s, TimeUnit.SECONDS)
                .withReadTimeout(s, TimeUnit.SECONDS)
                .withWriteTimeout(s, TimeUnit.SECONDS)
                .proceed(request)
        })
    LogHttp.interceptor()?.let(builder::addInterceptor)
    return builder.build()
}

inline fun <reified T> createApi(client: OkHttpClient, baseUrl: String): T =
    Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(T::class.java)
