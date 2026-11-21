package pt.ismai.farol.data.remote

import pt.ismai.farol.data.remote.dto.VirusTotalDomain
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface VirusTotalApi {
    /** 404 = VirusTotal has never seen the domain; 429 = the free quota (4 per minute) is used up. */
    @GET("domains/{domain}")
    suspend fun domain(
        @Header("x-apikey") apiKey: String,
        @Path("domain") domain: String,
    ): Response<VirusTotalDomain>

    companion object {
        const val BASE_URL = "https://www.virustotal.com/api/v3/"
    }
}
