package pt.ismai.farol.data.remote

import okhttp3.Interceptor

/** No logging in release. */
object LogHttp {
    fun interceptor(): Interceptor? = null
}
