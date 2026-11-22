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
        /** Asks for DNSSEC validation (the "do" bit); null leaves it to the resolver. */
        @Query("do") dnssecOk: Boolean? = null,
    ): Response<DnsResponse>

    companion object {
        const val BASE_URL = "https://dns.google/"
        const val TYPE_A = "A"
        const val TYPE_TXT = "TXT"
        const val TYPE_MX = "MX"
        const val TYPE_AAAA = "AAAA"
        const val TYPE_CAA = "CAA"
    }
}
