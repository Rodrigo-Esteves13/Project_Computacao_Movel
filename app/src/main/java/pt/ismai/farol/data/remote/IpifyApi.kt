package pt.ismai.farol.data.remote

import pt.ismai.farol.data.remote.dto.IpifyResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface IpifyApi {
    @GET("/")
    suspend fun myIp(@Query("format") format: String = "json"): Response<IpifyResponse>

    companion object {
        const val BASE_URL = "https://api.ipify.org/"
    }
}
