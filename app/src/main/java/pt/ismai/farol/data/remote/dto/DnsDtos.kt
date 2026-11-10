package pt.ismai.farol.data.remote.dto

import com.google.gson.annotations.SerializedName

/** dns.google response. Status 0 = OK, 3 = NXDOMAIN. */
data class DnsResponse(
    @SerializedName("Status") val status: Int = 0,
    @SerializedName("Answer") val answers: List<DnsRecord>? = null,
)

data class DnsRecord(
    val name: String? = null,
    val type: Int = 0,
    val data: String? = null,
)
