package pt.ismai.farol.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Only the part of the VirusTotal answer that Farol uses. */
data class VirusTotalDomain(val data: VirusTotalData? = null)

data class VirusTotalData(val attributes: VirusTotalAttributes? = null)

data class VirusTotalAttributes(
    @SerializedName("last_analysis_stats") val stats: VirusTotalStats? = null,
)

data class VirusTotalStats(
    val malicious: Int = 0,
    val suspicious: Int = 0,
    val harmless: Int = 0,
    val undetected: Int = 0,
)
