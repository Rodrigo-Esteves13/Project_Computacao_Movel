package pt.ismai.farol.domain.evaluators

import pt.ismai.farol.domain.ScoreWeights
import pt.ismai.farol.domain.Checks
import pt.ismai.farol.domain.model.CheckState
import pt.ismai.farol.domain.model.CheckId
import pt.ismai.farol.domain.model.Check
import java.util.Locale

/**
 * Weight 20. HSTS 6, CSP 5, X-Content-Type-Options 3, X-Frame-Options (or frame-ancestors in the CSP) 3,
 * Referrer-Policy 3. >=16 OK, >=8 WARNING, otherwise FAIL.
 */
object HeadersEvaluator {
    private const val ID = CheckId.HEADERS
    private const val WEIGHT = ScoreWeights.HEADERS
    private const val CSP = "content-security-policy"

    private const val EXPLANATION = "Security headers are instructions the site gives the browser " +
        "to protect its visitors. Not having them does not mean the site is dangerous, but it is less protected."

    private class Rule(val name: String, val points: Int, val purpose: String, val present: (Map<String, String>) -> Boolean)

    private val RULES = listOf(
        Rule("Strict-Transport-Security (HSTS)", ScoreWeights.HEADER_HSTS,
            "forcing the browser to always use a secure connection") { has(it, "strict-transport-security") },
        Rule("Content-Security-Policy (CSP)", ScoreWeights.HEADER_CSP,
            "limiting where the page can load scripts from, which stops injection attacks") { has(it, CSP) },
        Rule("X-Content-Type-Options", ScoreWeights.HEADER_CONTENT_TYPE,
            "stopping the browser from guessing the type of files") { has(it, "x-content-type-options") },
        Rule("X-Frame-Options", ScoreWeights.HEADER_FRAME,
            "preventing the site from being embedded in another page to trick clicks") {
            has(it, "x-frame-options") || it[CSP].orEmpty().contains("frame-ancestors")
        },
        Rule("Referrer-Policy", ScoreWeights.HEADER_REFERRER,
            "controlling which address is revealed to other sites") { has(it, "referrer-policy") },
    )

    fun evaluate(headers: Map<String, String>): Check {
        val normalized = headers.entries.associate { (k, v) -> k.lowercase(Locale.ROOT).trim() to v.lowercase(Locale.ROOT) }
        val (found, missing) = RULES.partition { it.present(normalized) }
        val points = found.sumOf { it.points }
        val state = when {
            points >= ScoreWeights.HEADERS_THRESHOLD_OK -> CheckState.OK
            points >= ScoreWeights.HEADERS_THRESHOLD_WARNING -> CheckState.WARNING
            else -> CheckState.FAIL
        }
        return Check(
            id = ID,
            title = Checks.title(ID),
            state = state,
            summary = "${found.size} of ${RULES.size} protections active",
            detail = detail(missing),
            points = points,
            weight = WEIGHT,
        )
    }

    private fun detail(missing: List<Rule>): String {
        val body = if (missing.isEmpty()) {
            "The site uses all the protections checked."
        } else {
            "Missing:\n" + missing.joinToString("\n") { "- ${it.name}: used for ${it.purpose}." }
        }
        return "$body\n\n$EXPLANATION"
    }

    private fun has(headers: Map<String, String>, name: String): Boolean = !headers[name].isNullOrBlank()
}
