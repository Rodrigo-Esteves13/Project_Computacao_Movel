package pt.ismai.farol.domain.evaluators

import pt.ismai.farol.domain.ScoreWeights
import pt.ismai.farol.domain.Checks
import pt.ismai.farol.domain.model.CheckState
import pt.ismai.farol.domain.model.CheckId
import pt.ismai.farol.domain.model.Check
import java.util.Locale

/**
 * Weight 25. >30 days = 25 OK; 8..30 = 15 WARNING; 0..7 = 5 WARNING; expired = 5 FAIL;
 * SSL error or no HTTPS = 0 FAIL. HTTPS ok with no known date counts as valid.
 */
object CertificateEvaluator {
    private const val ID = CheckId.CERTIFICATE
    private const val WEIGHT = ScoreWeights.CERTIFICATE

    private const val EXPLANATION = "The certificate is what lets the browser talk to the site in an encrypted " +
        "way and confirm it is really talking to that site."

    fun evaluate(httpsOk: Boolean, daysRemaining: Long?, issuer: String?, sslError: String?): Check = when {
        sslError != null -> fromError(sslError)
        !httpsOk -> create(
            CheckState.FAIL, 0, "No secure connection (HTTPS)",
            "The site did not accept a secure connection. Anything sent may be read or altered along the way.",
        )
        daysRemaining == null -> create(CheckState.OK, WEIGHT, "Valid", "The certificate is valid.${issuedBy(issuer)}")
        else -> byDays(daysRemaining, issuer)
    }

    private fun byDays(days: Long, issuer: String?): Check {
        val issuedNote = issuedBy(issuer)
        return when {
            days < 0 -> create(
                CheckState.FAIL, ScoreWeights.CERT_POINTS_CRITICAL, "Certificate expired",
                "The certificate expired ${plural(-days)} ago. The browser will show a danger warning.$issuedNote",
            )
            days <= ScoreWeights.CERT_DAYS_CRITICAL -> create(
                CheckState.WARNING, ScoreWeights.CERT_POINTS_CRITICAL, "Expires in ${plural(days)}",
                "The certificate is valid but about to expire. If it is not renewed in time, " +
                    "the site will start showing a danger warning.$issuedNote",
            )
            days <= ScoreWeights.CERT_DAYS_SAFE -> create(
                CheckState.WARNING, ScoreWeights.CERT_POINTS_MEDIUM, "Valid, expires in ${plural(days)}",
                "The certificate is valid but expires within a month. It should be renewed soon.$issuedNote",
            )
            else -> create(
                CheckState.OK, WEIGHT, "Valid, expires in ${plural(days)}",
                "The certificate is valid and not close to expiring.$issuedNote",
            )
        }
    }

    private fun fromError(error: String): Check {
        val text = error.lowercase(Locale.ROOT)
        val (summary, reason) = when {
            "expir" in text -> "Certificate expired" to "The certificate is no longer valid because it expired."
            "hostname" in text || "not verified" in text -> "The certificate does not match the site" to
                "The certificate was issued for a different name, not for this site."
            "trust" in text || "self" in text || "certification path" in text -> "Certificate not trusted" to
                "The certificate was not issued by a recognized authority (it may be self-signed)."
            else -> "Invalid certificate" to "Could not confirm that the certificate is valid."
        }
        return create(CheckState.FAIL, 0, summary, "$reason The browser will show a danger warning.")
    }

    private fun issuedBy(issuer: String?): String =
        if (issuer.isNullOrBlank()) "" else " Issued by $issuer."

    private fun plural(days: Long): String = if (days == 1L) "1 day" else "$days days"

    private fun create(state: CheckState, points: Int, summary: String, detail: String) = Check(
        id = ID,
        title = Checks.title(ID),
        state = state,
        summary = summary,
        detail = "$detail\n\n$EXPLANATION",
        points = points,
        weight = WEIGHT,
    )
}
