package pt.ismai.farol.domain

/**
 * Finds the domain or IP in text shared from another app ("Look at this https://x.com/a, it is great").
 * The result goes through [TargetNormalizer], so only a valid host is ever returned.
 */
object SharedText {
    private val WHITESPACE = Regex("\\s+")
    private val EDGE_PUNCTUATION = charArrayOf('(', ')', '[', ']', '<', '>', '"', '\'', ',', ';', '.', '!', '?')

    /** The host of the first link in [text], or of the first word that looks like a domain or IP; null if none. */
    fun extractTarget(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val words = text.trim().split(WHITESPACE).map { it.trim(*EDGE_PUNCTUATION) }.filter { it.isNotEmpty() }
        val link = words.firstOrNull { it.startsWith("http://", true) || it.startsWith("https://", true) }
        val candidate = link ?: words.firstOrNull { TargetNormalizer.parse(it) != null }
        return candidate?.let { TargetNormalizer.parse(it)?.value }
    }
}
