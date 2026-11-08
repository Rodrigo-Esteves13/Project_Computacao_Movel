package pt.ismai.farol.domain

import pt.ismai.farol.domain.model.Target
import pt.ismai.farol.domain.model.TargetType
import java.net.IDN
import java.util.Locale

/** Validates and normalizes what the user types (URL, domain or IPv4). */
object TargetNormalizer {
    private val SCHEMES = listOf("https://", "http://")
    private val SEPARATORS = charArrayOf('/', '?', '#', ':')
    private val OCTET = Regex("^(0|[1-9][0-9]{0,2})$")
    private val LABEL = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$")
    private val TLD_LETTERS = Regex("^[a-z]{2,}$")
    private const val PUNYCODE_PREFIX = "xn--"
    private const val IPV4_OCTETS = 4
    private const val OCTET_MAX = 255
    private const val LABEL_MAX = 63
    private const val DOMAIN_MAX = 253
    private const val LABELS_MIN = 2

    /** null if invalid (empty, no dot, invalid characters, IPv6 not supported in the MVP). */
    fun parse(input: String): Target? {
        val host = extractHost(input) ?: return null
        if (isIpv4(host)) return Target(host, TargetType.IP)
        val ascii = toAscii(host) ?: return null
        return if (isDomain(ascii)) Target(ascii, TargetType.DOMAIN) else null
    }

    /** trim, lowercase, no scheme, cut at the first '/', '?', '#' or ':' (port). */
    private fun extractHost(input: String): String? {
        var text = input.trim().lowercase(Locale.ROOT)
        SCHEMES.firstOrNull { text.startsWith(it) }?.let { text = text.removePrefix(it) }
        val cut = text.indexOfAny(SEPARATORS)
        if (cut >= 0) text = text.substring(0, cut)
        text = text.removeSuffix(".")
        return text.ifEmpty { null }
    }

    private fun isIpv4(text: String): Boolean {
        val parts = text.split('.')
        return parts.size == IPV4_OCTETS &&
            parts.all { OCTET.matches(it) && it.toInt() <= OCTET_MAX }
    }

    /** IDN (e.g. accents) -> punycode. null if Java rejects the name. */
    private fun toAscii(text: String): String? =
        try {
            IDN.toASCII(text).lowercase(Locale.ROOT)
        } catch (e: IllegalArgumentException) {
            null
        }

    private fun isDomain(text: String): Boolean {
        if (text.length > DOMAIN_MAX) return false
        val labels = text.split('.')
        if (labels.size < LABELS_MIN) return false
        if (labels.any { it.length > LABEL_MAX || !LABEL.matches(it) }) return false
        val tld = labels.last()
        return TLD_LETTERS.matches(tld) || tld.startsWith(PUNYCODE_PREFIX)
    }
}
