package pt.ismai.farol.data.remote.dto

import com.google.gson.JsonArray

data class RdapDomain(val events: List<RdapEvent>? = null)

data class RdapEvent(
    val eventAction: String? = null,
    val eventDate: String? = null,
)

data class RdapIp(
    val name: String? = null,
    val country: String? = null,
    val entities: List<RdapEntity>? = null,
)

/** RDAP entity; [vcardArray] is a jCard: ["vcard", [[field, {}, type, value], ...]]. */
data class RdapEntity(
    val roles: List<String>? = null,
    val vcardArray: JsonArray? = null,
    val entities: List<RdapEntity>? = null,
)
