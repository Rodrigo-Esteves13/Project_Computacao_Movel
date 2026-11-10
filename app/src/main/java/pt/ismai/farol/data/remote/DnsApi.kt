package pt.ismai.farol.data.remote

import pt.ismai.farol.data.remote.dto.DnsResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface DnsApi {
    @GET("resolve")
    suspend fun resolve(
        @Query("name") name: String,
        @Query("type") type: String,
    ): Response<DnsResponse>

    companion object {
        const val BASE_URL = "https://dns.google/"
        const val TYPE_A = "A"
        const val TYPE_TXT = "TXT"
        const val TYPE_MX = "MX"
    }
}
