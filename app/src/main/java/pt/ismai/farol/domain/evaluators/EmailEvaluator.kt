package pt.ismai.farol.domain.evaluators

import pt.ismai.farol.domain.ScoreWeights
import pt.ismai.farol.domain.Checks
import pt.ismai.farol.domain.model.CheckId
import pt.ismai.farol.domain.model.Check
import java.util.Locale

/**
 * Weight 20. spf/dmarc = already-cleaned TXT record text, or null if absent.
 * SPF (v=spf1) 8, DMARC (v=DMARC1) 6, DMARC with p=quarantine or p=reject 6.
 */
object EmailEvaluator {
    private const val ID = CheckId.EMAIL
    private const val WEIGHT = ScoreWeights.EMAIL
    private val STRONG_POLICIES = setOf("quarantine", "reject")

    private const val EXPLANATION = "SPF and DMARC are public records that say who may send email on behalf of " +
        "this domain. Without them, it is easier for someone to send fake emails pretending to be this site."

    fun evaluate(spf: String?, dmarc: String?): Check {
        val hasSpf = beginsWith(spf, "v=spf1")
        val hasDmarc = beginsWith(dmarc, "v=dmarc1")
        val strong = hasDmarc && policy(dmarc.orEmpty()) in STRONG_POLICIES
        val points = (if (hasSpf) ScoreWeights.EMAIL_SPF else 0) +
            (if (hasDmarc) ScoreWeights.EMAIL_DMARC else 0) +
            (if (strong) ScoreWeights.EMAIL_DMARC_POLICY else 0)
        return Check(
            id = ID,
            title = Checks.title(ID),
            state = stateForPoints(points, WEIGHT),
            summary = summary(hasSpf, hasDmarc, strong),
            detail = detail(hasSpf, hasDmarc, strong),
            points = points,
            weight = WEIGHT,
        )
    }

    private fun summary(spf: Boolean, dmarc: Boolean, strong: Boolean): String = when {
        spf && strong -> "Protected against fake emails"
        spf || dmarc -> "Partial protection against fake emails"
        else -> "No protection against fake emails"
    }

    private fun detail(spf: Boolean, dmarc: Boolean, strong: Boolean): String {
        val lines = listOf(
            if (spf) "SPF: present." else "SPF: missing.",
            when {
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
}
