package pt.ismai.farol.data.remote

import pt.ismai.farol.data.remote.dto.RdapDomain
import pt.ismai.farol.data.remote.dto.RdapIp
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path

interface RdapApi {
    @Headers(ACCEPT)
    @GET("domain/{domain}")
    suspend fun domain(@Path("domain") domain: String): Response<RdapDomain>

    @Headers(ACCEPT)
    @GET("ip/{ip}")
    suspend fun ip(@Path("ip") ip: String): Response<RdapIp>

    companion object {
        const val BASE_URL = "https://rdap.org/"
        private const val ACCEPT = "Accept: application/rdap+json, application/json"
    }
}
