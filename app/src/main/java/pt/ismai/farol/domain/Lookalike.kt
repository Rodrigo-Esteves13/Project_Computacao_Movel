package pt.ismai.farol.domain

import java.net.IDN

/** Why a domain may be imitating something else. */
enum class LookalikeKind { MIXED_ALPHABETS, OTHER_ALPHABET, SIMILAR_TO_BRAND }

/**
 * [shown] is what the user would see in a browser (the Unicode form for international names);
 * [brand] is the well-known name that is being imitated, for [LookalikeKind.SIMILAR_TO_BRAND].
 */
data class LookalikeWarning(val kind: LookalikeKind, val shown: String, val brand: String? = null)

/**
 * Warns about domains made to look like another one. It works offline and is a hint, not a verdict:
 * it knows only a short list of famous names, and an exact match is never flagged because the
 * real owner of a brand can use any domain ending.
 */
object Lookalike {
    private const val PUNYCODE_PREFIX = "xn--"
    private const val MIN_NAME_FOR_TYPO = 5
    private const val MIN_BRAND_IN_WORDS = 5

    private val BRANDS = setOf(
        "google", "paypal", "facebook", "amazon", "microsoft", "apple", "netflix", "instagram",
        "whatsapp", "linkedin", "twitter", "youtube", "outlook", "github", "dropbox", "spotify",
        "binance", "coinbase", "revolut", "santander", "millenniumbcp", "novobanco", "mbway", "caixa",
    )

    /** Characters often used in place of letters: 0 for o, 1 for l, and so on. */
    private val SUBSTITUTES = mapOf('0' to 'o', '1' to 'l', '3' to 'e', '5' to 's', '4' to 'a')

    /** [domain] must be an ASCII (punycode) domain as produced by [TargetNormalizer]. */
    fun detect(domain: String): LookalikeWarning? {
        val labels = domain.lowercase().split('.')
        if (labels.size < 2) return null
        return alphabetWarning(domain, labels) ?: brandWarning(domain, labels)
    }

    private fun alphabetWarning(domain: String, labels: List<String>): LookalikeWarning? {
        if (labels.none { it.startsWith(PUNYCODE_PREFIX) }) return null
        val shown = runCatching { IDN.toUnicode(domain) }.getOrDefault(domain)
        val scriptsPerLabel = shown.split('.').map { scriptsOf(it) }
        return when {
            scriptsPerLabel.any { it.size > 1 } -> LookalikeWarning(LookalikeKind.MIXED_ALPHABETS, shown)
            scriptsPerLabel.any { s -> s.any { it != Character.UnicodeScript.LATIN } } ->
                LookalikeWarning(LookalikeKind.OTHER_ALPHABET, shown)
            else -> null // accents only (cafe with an accent): ordinary international name
        }
    }

    private fun scriptsOf(label: String): Set<Character.UnicodeScript> = label
        .filter { it.isLetter() }
        .map { Character.UnicodeScript.of(it.code) }
        .toSet()

    private fun brandWarning(domain: String, labels: List<String>): LookalikeWarning? {
        val name = labels[labels.size - 2]
        if (name in BRANDS) return null
        val brand = similarBrand(name) ?: return null
        return LookalikeWarning(LookalikeKind.SIMILAR_TO_BRAND, domain, brand)
    }

    private fun similarBrand(name: String): String? {
        val plain = name.map { SUBSTITUTES[it] ?: it }.joinToString("").replace("rn", "m").replace("vv", "w")
        if (plain in BRANDS) return plain
        if (name.length >= MIN_NAME_FOR_TYPO) {
            BRANDS.firstOrNull { it.length >= MIN_NAME_FOR_TYPO && oneEditApart(plain, it) }?.let { return it }
        }
        // "paypal-login", "secure-paypal": the brand is one word of a longer name.
        return name.split('-').firstOrNull { it in BRANDS && it.length >= MIN_BRAND_IN_WORDS }
            .takeIf { name.contains('-') }
    }

    /** true if the texts differ by exactly one insertion, deletion or substitution. */
    internal fun oneEditApart(a: String, b: String): Boolean {
        if (a == b || kotlin.math.abs(a.length - b.length) > 1) return false
        var i = 0
        while (i < a.length && i < b.length && a[i] == b[i]) i++
        return when {
            a.length == b.length -> a.substring(i + 1) == b.substring(i + 1)
            a.length > b.length -> a.substring(i + 1) == b.substring(i)
            else -> a.substring(i) == b.substring(i + 1)
        }
    }

    /** English sentence for the shared report. */
    fun describe(w: LookalikeWarning): String = when (w.kind) {
        LookalikeKind.MIXED_ALPHABETS -> "the name mixes alphabets (shown as ${w.shown})"
        LookalikeKind.OTHER_ALPHABET -> "the name uses another alphabet (shown as ${w.shown})"
        LookalikeKind.SIMILAR_TO_BRAND -> "the name looks like ${w.brand}"
    }
}
