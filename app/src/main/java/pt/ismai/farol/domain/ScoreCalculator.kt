package pt.ismai.farol.domain

import pt.ismai.farol.domain.model.CheckState
import pt.ismai.farol.domain.model.Check
import kotlin.math.roundToInt

/** score = round(sum of points / sum of weights of available checks * 100); null if there are no weights. */
object ScoreCalculator {
    fun calculate(checks: List<Check>): Int? {
        val counted = checks.filter { it.state != CheckState.UNAVAILABLE && it.weight > 0 }
        val weights = counted.sumOf { it.weight }
        if (weights <= 0) return null
        val points = counted.sumOf { it.points.coerceIn(0, it.weight) }
        return (points.toDouble() / weights * ScoreWeights.SCALE).roundToInt()
    }
}
