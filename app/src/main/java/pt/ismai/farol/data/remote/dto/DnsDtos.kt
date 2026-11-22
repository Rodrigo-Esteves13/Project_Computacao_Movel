package pt.ismai.farol.data.remote.dto

import com.google.gson.annotations.SerializedName

/** dns.google response. Status 0 = OK, 3 = NXDOMAIN. */
data class DnsResponse(
    @SerializedName("Status") val status: Int = 0,
    @SerializedName("Answer") val answers: List<DnsRecord>? = null,
    /** "AD" flag: the resolver checked the DNSSEC signatures of the whole answer. */
    @SerializedName("AD") val authenticated: Boolean = false,
)

data class DnsRecord(
    val name: String? = null,
    val type: Int = 0,
    val data: String? = null,
)
