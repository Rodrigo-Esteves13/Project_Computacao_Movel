package pt.ismai.farol.data.remote

import pt.ismai.farol.data.remote.dto.InternetDbResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface InternetDbApi {
    /** 404 = no data about the IP (not an error). */
    @GET("{ip}")
    suspend fun lookup(@Path("ip") ip: String): Response<InternetDbResponse>

    companion object {
        const val BASE_URL = "https://internetdb.shodan.io/"
    }
}
