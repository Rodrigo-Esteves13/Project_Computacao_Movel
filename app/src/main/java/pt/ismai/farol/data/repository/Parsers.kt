package pt.ismai.farol.data.repository

import com.google.gson.JsonArray
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import pt.ismai.farol.data.remote.dto.DnsResponse
import pt.ismai.farol.data.remote.dto.RdapEntity
import pt.ismai.farol.data.remote.dto.RdapEvent
import pt.ismai.farol.domain.cleanTxt
import pt.ismai.farol.domain.model.AnalysisResult
import pt.ismai.farol.domain.model.Check
import pt.ismai.farol.domain.model.CheckState

/** Pure logic for interpreting the responses (testable without Android). */

private const val DNS_TYPE_A = 1
private const val DNS_TYPE_MX = 15
private const val DNS_TYPE_TXT = 16
private const val MAX_OCTET = 255
private const val MIN_LABELS = 2
private val REGEX_ORG = Regex("""(?:^|,)\s*O=([^,]+)""")
private val REGEX_CN = Regex("""(?:^|,)\s*CN=([^,]+)""")
private val REGEX_IPV4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

fun firstIpv4(response: DnsResponse?): String? =
    response?.answers.orEmpty()
        .filter { it.type == DNS_TYPE_A }
        .mapNotNull { it.data?.trim() }
        .firstOrNull { isIpv4(it) }

private fun isIpv4(text: String): Boolean {
    val m = REGEX_IPV4.matchEntire(text) ?: return false
    return m.groupValues.drop(1).all { it.toInt() <= MAX_OCTET }
}

/** First TXT record (quotes removed) that starts with [prefix], case-insensitive. */
fun txtWithPrefix(response: DnsResponse?, prefix: String): String? =
    response?.answers.orEmpty()
        .filter { it.type == DNS_TYPE_TXT }
        .mapNotNull { it.data?.cleanTxt()?.trim() }
        .firstOrNull { it.startsWith(prefix, ignoreCase = true) }

/** [host] followed by its parent domains (up to [levels]), always keeping >= 2 labels. */
fun hostHierarchy(host: String, levels: Int = 2): List<String> {
    val labels = host.trim('.').split('.')
    return (0..levels).mapNotNull { n ->
        labels.drop(n).takeIf { it.size >= MIN_LABELS }?.joinToString(".")
    }
}

/** MX server names ("10 aspmx.l.google.com." -> "aspmx.l.google.com"), in order of appearance. */
fun mxServers(response: DnsResponse?): List<String> =
    response?.answers.orEmpty()
        .filter { it.type == DNS_TYPE_MX }
        .mapNotNull { it.data?.trim()?.substringAfterLast(' ')?.trimEnd('.')?.lowercase() }
        .filter { it.isNotEmpty() }
        .distinct()

private const val REGISTRATION_ACTION = "registration"
private const val ISO_DATE_LENGTH = 10
private const val ROLE_REGISTRANT = "registrant"
private const val POS_PROPERTIES = 1
private const val POS_VALUE = 3

/** Whole days since the oldest "registration" event; null if missing or unreadable. */
fun daysSinceRegistration(events: List<RdapEvent>?, now: Instant): Long? {
    val registeredAt = events.orEmpty()
        .filter { it.eventAction.equals(REGISTRATION_ACTION, ignoreCase = true) }
        .mapNotNull { parseDate(it.eventDate) }
        .minOrNull() ?: return null
    return ChronoUnit.DAYS.between(registeredAt, now).coerceAtLeast(0)
}

private fun parseDate(text: String?): Instant? {
    if (text.isNullOrBlank()) return null
    return runCatching { Instant.parse(text) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
        ?: runCatching {
            LocalDate.parse(text.take(ISO_DATE_LENGTH)).atStartOfDay(ZoneOffset.UTC).toInstant()
        }.getOrNull()
}

/** Registrant name (vcard fn, otherwise org) in a list of RDAP entities, searched depth-first. */
fun registrantName(entities: List<RdapEntity>?): String? {
    for (e in entities.orEmpty()) {
        val isRegistrant = e.roles.orEmpty().any { it.equals(ROLE_REGISTRANT, ignoreCase = true) }
        val name = if (isRegistrant) vcardValue(e.vcardArray, "fn") ?: vcardValue(e.vcardArray, "org") else null
        val result = name ?: registrantName(e.entities)
        if (result != null) return result
    }
    return null
}

/** Text value of a jCard field; accepts a plain value or a list (uses the first text). */
fun vcardValue(vcard: JsonArray?, field: String): String? {
    val properties = vcard?.takeIf { it.size() > POS_PROPERTIES }?.get(POS_PROPERTIES)
        ?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
    for (p in properties) {
        if (!p.isJsonArray) continue
        val arr = p.asJsonArray
        if (arr.size() <= POS_VALUE || arr[0].asString != field) continue
        val v = arr[POS_VALUE]
        val text = if (v.isJsonArray) {
            v.asJsonArray.firstOrNull { it.isJsonPrimitive }?.asString
        } else {
            v.takeIf { it.isJsonPrimitive }?.asString
        }
        if (!text.isNullOrBlank()) return text.trim()
    }
    return null
}

/** Days until expiry (negative if already expired); null without a date. */
fun daysRemaining(expiresAt: Instant?, now: Instant): Long? =
    expiresAt?.let { ChronoUnit.DAYS.between(now, it) }

/** Readable issuer name from the DN ("CN=R3,O=Lets Encrypt,C=US" -> "Lets Encrypt"). */
fun issuerName(dn: String?): String? {
    if (dn.isNullOrBlank()) return null
    val name = REGEX_ORG.find(dn)?.groupValues?.get(1) ?: REGEX_CN.find(dn)?.groupValues?.get(1)
    return (name ?: dn).trim()
}

/** true if the score or the status of any check changed compared to the previous analysis. */
fun resultChanged(previous: AnalysisResult?, latest: AnalysisResult): Boolean {
    if (previous == null) return false
    if (previous.score != latest.score) return true
    val before = previous.checks.associate { it.id to it.state }
    val after = latest.checks.associate { it.id to it.state }
    return before != after
}

/** true if all checks are unavailable (nothing useful to save). */
fun allUnavailable(checks: List<Check>): Boolean = checks.all { it.state == CheckState.UNAVAILABLE }
