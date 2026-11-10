package pt.ismai.farol.data.remote

import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor

/** Logging in debug builds only (request/response line only, no body or headers). */
object LogHttp {
    fun interceptor(): Interceptor? = HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC)
}
