package pt.ismai.farol.domain.evaluators

import pt.ismai.farol.domain.ScoreWeights
import pt.ismai.farol.domain.Checks
import pt.ismai.farol.domain.model.CheckState
import pt.ismai.farol.domain.model.CheckId
import pt.ismai.farol.domain.model.Check
import java.util.Locale

/**
 * Weight 20. HSTS 6 (3 if its max-age is shorter than 6 months, 0 if it is 0), CSP 5,
 * X-Content-Type-Options 3, X-Frame-Options (or frame-ancestors in the CSP) 3, Referrer-Policy 3.
 * >=16 OK, >=8 WARNING, otherwise FAIL.
 */
object HeadersEvaluator {
    private const val ID = CheckId.HEADERS
    private const val WEIGHT = ScoreWeights.HEADERS
    private const val CSP = "content-security-policy"
    private const val HSTS = "strict-transport-security"
    private val MAX_AGE = Regex("max-age\\s*=\\s*\"?(\\d+)")

    private const val EXPLANATION = "Security headers are instructions the site gives the browser " +
        "to protect its visitors. Not having them does not mean the site is dangerous, but it is less protected."

    /** [earned] is how many of [points] the site gets: 0 when missing, less than [points] when weak. */
    private class Rule(val name: String, val points: Int, val purpose: String, val earned: (Map<String, String>) -> Int)

    private val RULES = listOf(
        Rule("Strict-Transport-Security (HSTS)", ScoreWeights.HEADER_HSTS,
            "forcing the browser to always use a secure connection") { hstsPoints(it[HSTS]) },
        Rule("Content-Security-Policy (CSP)", ScoreWeights.HEADER_CSP,
            "limiting where the page can load scripts from, which stops injection attacks") {
            full(has(it, CSP), ScoreWeights.HEADER_CSP)
        },
        Rule("X-Content-Type-Options", ScoreWeights.HEADER_CONTENT_TYPE,
            "stopping the browser from guessing the type of files") {
            full(has(it, "x-content-type-options"), ScoreWeights.HEADER_CONTENT_TYPE)
        },
        Rule("X-Frame-Options", ScoreWeights.HEADER_FRAME,
            "preventing the site from being embedded in another page to trick clicks") {
            full(has(it, "x-frame-options") || it[CSP].orEmpty().contains("frame-ancestors"), ScoreWeights.HEADER_FRAME)
        },
        Rule("Referrer-Policy", ScoreWeights.HEADER_REFERRER,
            "controlling which address is revealed to other sites") {
            full(has(it, "referrer-policy"), ScoreWeights.HEADER_REFERRER)
        },
    )

    fun evaluate(headers: Map<String, String>): Check {
        val normalized = headers.entries.associate { (k, v) -> k.lowercase(Locale.ROOT).trim() to v.lowercase(Locale.ROOT) }
        val earned = RULES.map { it to it.earned(normalized) }
        val points = earned.sumOf { it.second }
        val state = when {
            points >= ScoreWeights.HEADERS_THRESHOLD_OK -> CheckState.OK
            points >= ScoreWeights.HEADERS_THRESHOLD_WARNING -> CheckState.WARNING
            else -> CheckState.FAIL
        }
        return Check(
            id = ID,
            title = Checks.title(ID),
            state = state,
            summary = "${earned.count { it.second > 0 }} of ${RULES.size} protections active",
            detail = detail(
                missing = earned.filter { it.second == 0 }.map { it.first },
                weak = earned.filter { it.second in 1 until it.first.points }.map { it.first },
            ),
            points = points,
            weight = WEIGHT,
        )
    }

    /** HSTS only protects for as long as its max-age: under 6 months it is weak, 0 switches it off. */
    private fun hstsPoints(value: String?): Int {
        if (value.isNullOrBlank()) return 0
        val seconds = MAX_AGE.find(value)?.groupValues?.get(1)?.toLongOrNull() ?: return ScoreWeights.HEADER_HSTS_WEAK
        return when {
            seconds <= 0 -> 0
            seconds < ScoreWeights.HSTS_MIN_MAX_AGE_S -> ScoreWeights.HEADER_HSTS_WEAK
            else -> ScoreWeights.HEADER_HSTS
        }
    }

    private fun detail(missing: List<Rule>, weak: List<Rule>): String {
        val parts = mutableListOf<String>()
        if (missing.isNotEmpty()) {
            parts += "Missing:\n" + missing.joinToString("\n") { "- ${it.name}: used for ${it.purpose}." }
        }
        if (weak.isNotEmpty()) {
            parts += "Present but weak:\n" + weak.joinToString("\n") { "- ${it.name}: it is valid for less than six months." }
        }
        if (parts.isEmpty()) parts += "The site uses all the protections checked."
        return parts.joinToString("\n\n") + "\n\n$EXPLANATION"
    }

    private fun full(present: Boolean, points: Int): Int = if (present) points else 0

    private fun has(headers: Map<String, String>, name: String): Boolean = !headers[name].isNullOrBlank()
}
