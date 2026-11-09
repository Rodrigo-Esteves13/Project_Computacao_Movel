package pt.ismai.farol.domain.evaluators

import pt.ismai.farol.domain.model.CheckState

/** Maximum = OK, zero = FAIL, anything else = WARNING. Shared by evaluators without their own thresholds. */
internal fun stateForPoints(points: Int, weight: Int): CheckState = when {
    points >= weight -> CheckState.OK
    points <= 0 -> CheckState.FAIL
    else -> CheckState.WARNING
}
