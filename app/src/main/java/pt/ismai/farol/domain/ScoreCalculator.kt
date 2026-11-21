package pt.ismai.farol.domain

import pt.ismai.farol.domain.model.Check
import pt.ismai.farol.domain.model.CheckId
import pt.ismai.farol.domain.model.CheckState
import kotlin.math.roundToInt

/** Why a score was held down: the check that failed and the highest score allowed because of it. */
data class ScoreCap(val checkId: String, val limit: Int)

/** How much evidence a score rests on: [available] of [total] scored checks produced data. */
data class ScoreCoverage(val available: Int, val total: Int)

/**
 * score = round(sum of points / sum of weights of available checks * 100); null if there are no weights.
 *
 * A critical certificate, exposure or reputation result also caps the score, so a site with an invalid
 * certificate cannot look trustworthy just because its other checks are perfect.
 */
object ScoreCalculator {
    fun calculate(checks: List<Check>): Int? {
        val counted = checks.filter { it.state != CheckState.UNAVAILABLE && it.weight > 0 }
        val weights = counted.sumOf { it.weight }
        if (weights <= 0) return null
        val points = counted.sumOf { it.points.coerceIn(0, it.weight) }
        val raw = (points.toDouble() / weights * ScoreWeights.SCALE).roundToInt()
        return minOf(raw, cap(checks)?.limit ?: ScoreWeights.SCALE)
    }

    /** The lowest cap that applies to [checks], or null if none does. */
    fun cap(checks: List<Check>): ScoreCap? = CAPS
        .filter { (id, _) -> checks.any { it.id == id && it.state == CheckState.FAIL } }
        .minByOrNull { it.second }
        ?.let { (id, limit) -> ScoreCap(id, limit) }

    /** Scored checks that answered, out of all scored checks (informative ones are not counted). */
    fun coverage(checks: List<Check>): ScoreCoverage {
        // An unavailable check has weight 0, so it is recognised as scored by its id.
        val scored = checks.filter { it.weight > 0 || (it.state == CheckState.UNAVAILABLE && it.id in SCORED_IDS) }
        return ScoreCoverage(scored.count { it.state != CheckState.UNAVAILABLE }, scored.size)
    }

    private val SCORED_IDS = setOf(
        CheckId.CERTIFICATE, CheckId.AGE, CheckId.HEADERS, CheckId.EMAIL, CheckId.EXPOSURE,
        CheckId.REPUTATION,
    )

    private val CAPS = listOf(
        CheckId.CERTIFICATE to ScoreWeights.CAP_CERTIFICATE_FAIL,
        CheckId.EXPOSURE to ScoreWeights.CAP_EXPOSURE_FAIL,
        CheckId.REPUTATION to ScoreWeights.CAP_REPUTATION_FAIL,
    )
}
