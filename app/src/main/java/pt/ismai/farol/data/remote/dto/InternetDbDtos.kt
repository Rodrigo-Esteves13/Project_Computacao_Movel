package pt.ismai.farol.data.remote.dto

/** InternetDB (Shodan) response. 404 = no data. */
data class InternetDbResponse(
    val ip: String? = null,
    val ports: List<Int>? = null,
    val vulns: List<String>? = null,
)
