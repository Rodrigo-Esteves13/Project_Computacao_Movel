package pt.ismai.farol.domain.evaluators

import pt.ismai.farol.domain.ScoreWeights
import pt.ismai.farol.domain.Checks
import pt.ismai.farol.domain.model.CheckId
import pt.ismai.farol.domain.model.Check
import java.util.Locale

/**
 * Weight 20. spf/dmarc = already-cleaned TXT record text, or null if absent.
 * SPF (v=spf1) 8 (4 if it ends in ?all, 0 if it ends in +all because anyone may send), DMARC (v=DMARC1) 6,
 * DMARC with p=quarantine or p=reject 6 (3 if pct is below 100).
 */
object EmailEvaluator {
    private const val ID = CheckId.EMAIL
    private const val WEIGHT = ScoreWeights.EMAIL
    private val STRONG_POLICIES = setOf("quarantine", "reject")

    private const val EXPLANATION = "SPF and DMARC are public records that say who may send email on behalf of " +
        "this domain. Without them, it is easier for someone to send fake emails pretending to be this site."

    fun evaluate(spf: String?, dmarc: String?): Check {
        val hasSpf = beginsWith(spf, "v=spf1")
        val spfPoints = if (hasSpf) spfPoints(allQualifier(spf.orEmpty())) else 0
        val hasDmarc = beginsWith(dmarc, "v=dmarc1")
        val strong = hasDmarc && policy(dmarc.orEmpty()) in STRONG_POLICIES
        val partial = strong && (pct(dmarc.orEmpty()) ?: ScoreWeights.DMARC_FULL_PCT) < ScoreWeights.DMARC_FULL_PCT
        val policyPoints = when {
            !strong -> 0
            partial -> ScoreWeights.EMAIL_DMARC_POLICY_PARTIAL
            else -> ScoreWeights.EMAIL_DMARC_POLICY
        }
        val points = spfPoints + (if (hasDmarc) ScoreWeights.EMAIL_DMARC else 0) + policyPoints
        return Check(
            id = ID,
            title = Checks.title(ID),
            state = stateForPoints(points, WEIGHT),
            summary = summary(hasSpf, hasDmarc, strong),
            detail = detail(hasSpf, allQualifier(spf.orEmpty()), hasDmarc, strong, partial),
            points = points,
            weight = WEIGHT,
        )
    }

    /** The qualifier of the "all" term that ends an SPF record: "-", "~", "?" or "+" (a bare "all" is "+"); null if none. */
    private fun allQualifier(spf: String): Char? {
        val term = spf.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).lastOrNull { it.endsWith("all") && it.length <= 4 }
            ?: return null
        return if (term == "all") '+' else term.first().takeIf { it in "-~?+" }
    }

    private fun spfPoints(qualifier: Char?): Int = when (qualifier) {
        '+' -> 0
        '?' -> ScoreWeights.EMAIL_SPF_NEUTRAL
        else -> ScoreWeights.EMAIL_SPF // -all, ~all, or a record that ends with redirect=
    }

    private fun summary(spf: Boolean, dmarc: Boolean, strong: Boolean): String = when {
        spf && strong -> "Protected against fake emails"
        spf || dmarc -> "Partial protection against fake emails"
        else -> "No protection against fake emails"
    }

    private fun detail(spf: Boolean, qualifier: Char?, dmarc: Boolean, strong: Boolean, partial: Boolean): String {
        val lines = listOf(
            when {
                !spf -> "SPF: missing."
                qualifier == '+' -> "SPF: present, but it ends in +all, which lets anyone send as this domain."
                qualifier == '?' -> "SPF: present, but it ends in ?all, which says nothing about unknown senders."
                else -> "SPF: present."
            },
            when {
                strong && partial -> "DMARC: present and strict, but it is applied to only part of the emails (pct below 100)."
                strong -> "DMARC: present and tells receivers to reject or quarantine fake emails."
                dmarc -> "DMARC: present, but only monitors (does not block fake emails)."
                else -> "DMARC: missing."
            },
        )
        return lines.joinToString("\n") + "\n\n$EXPLANATION"
    }

    private fun beginsWith(record: String?, prefix: String): Boolean =
        record?.trim()?.lowercase(Locale.ROOT)?.startsWith(prefix) == true

    /** Value of the "p=" tag (not to be confused with "sp="). */
    private fun policy(dmarc: String): String? = dmarc.split(';')
        .map { it.trim().lowercase(Locale.ROOT) }
        .firstOrNull { it.startsWith("p=") }
        ?.removePrefix("p=")
        ?.trim()

    /** Value of the "pct=" tag as a number; null if absent or not a number. */
    private fun pct(dmarc: String): Int? = dmarc.split(';')
        .map { it.trim().lowercase(Locale.ROOT) }
        .firstOrNull { it.startsWith("pct=") }
        ?.removePrefix("pct=")
        ?.trim()
        ?.toIntOrNull()
}
