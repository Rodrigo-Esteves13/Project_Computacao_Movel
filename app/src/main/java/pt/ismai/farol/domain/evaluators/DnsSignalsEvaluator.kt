package pt.ismai.farol.domain.evaluators

import pt.ismai.farol.domain.Checks
import pt.ismai.farol.domain.ScoreWeights
import pt.ismai.farol.domain.model.Check
import pt.ismai.farol.domain.model.CheckId
import pt.ismai.farol.domain.model.CheckState

/**
 * Informative: weight 0, never changes the score. Each signal is true, false, or null when it could
 * not be read; with nothing readable the check is UNAVAILABLE. WARNING if neither DNSSEC nor CAA is
 * in place, OK otherwise. IPv6 is only reported.
 */
object DnsSignalsEvaluator {
    private const val ID = CheckId.DNS_SIGNALS

    private const val EXPLANATION = "These are public DNS settings that make it harder to hijack or imitate " +
        "a domain. Their absence is common and does not mean the site is dangerous, so they do not count toward the score."
    private const val NOTHING_READ = "The DNS records could not be read."

    fun evaluate(dnssec: Boolean?, caa: Boolean?, ipv6: Boolean?): Check {
        if (dnssec == null && caa == null && ipv6 == null) return Checks.unavailable(ID, NOTHING_READ)
        val lines = listOfNotNull(
            dnssec?.let { if (it) "DNSSEC: on. DNS answers for this domain are digitally signed, so they are hard to forge." else "DNSSEC: off. DNS answers for this domain are not signed." },
            caa?.let { if (it) "CAA: set. Only the listed authorities may issue certificates for this domain." else "CAA: not set. Any certificate authority may issue certificates for this domain." },
            ipv6?.let { if (it) "IPv6: supported." else "IPv6: not supported." },
        )
        val summary = listOfNotNull(
            dnssec?.let { "DNSSEC ${if (it) "on" else "off"}" },
            caa?.let { "CAA ${if (it) "set" else "not set"}" },
            ipv6?.let { "IPv6 ${if (it) "yes" else "no"}" },
        ).joinToString(", ")
        val hardened = dnssec == true || caa == true
        return Check(
            id = ID,
            title = Checks.title(ID),
            state = if (hardened) CheckState.OK else CheckState.WARNING,
            summary = summary,
            detail = lines.joinToString("\n") + "\n\n$EXPLANATION",
            points = 0,
            weight = ScoreWeights.DNS_SIGNALS,
        )
    }
}
