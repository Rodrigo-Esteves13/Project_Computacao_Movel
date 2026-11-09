package pt.ismai.farol.domain.evaluators

import pt.ismai.farol.domain.ScoreWeights
import pt.ismai.farol.domain.Checks
import pt.ismai.farol.domain.model.CheckState
import pt.ismai.farol.domain.model.CheckId
import pt.ismai.farol.domain.model.Check

/** Weight 20. null = no registration date (UNAVAILABLE). >365 = 20; 90..365 = 12; 30..89 = 6; <30 = 0. */
object AgeEvaluator {
    private const val ID = CheckId.AGE
    private const val WEIGHT = ScoreWeights.DOMAIN_AGE

    private const val EXPLANATION = "Scam sites often use domains registered only a few days or " +
        "weeks ago. An old domain guarantees nothing, but a very recent one calls for caution."
    private const val NO_DATE = "Could not find out when the domain was registered. " +
        "Some registries (such as many .pt domains) do not publish this information."

    fun evaluate(daysSinceRegistration: Long?): Check {
        val days = daysSinceRegistration ?: return Checks.unavailable(ID, NO_DATE)
        val summary = "Registered ${duration(days)} ago"
        return when {
            days > ScoreWeights.AGE_DAYS_OLD -> create(
                CheckState.OK, WEIGHT, summary, "The domain has existed for more than a year.",
            )
            days >= ScoreWeights.AGE_DAYS_MEDIUM -> create(
                CheckState.WARNING, ScoreWeights.AGE_POINTS_MEDIUM, summary, "The domain is between three months and a year old.",
            )
            days >= ScoreWeights.AGE_DAYS_YOUNG -> create(
                CheckState.WARNING, ScoreWeights.AGE_POINTS_YOUNG, summary, "The domain is less than three months old.",
            )
            else -> create(
                CheckState.FAIL, 0, "Very recent: registered ${duration(days)} ago",
                "The domain was registered less than a month ago.",
            )
        }
    }

    /** Readable text: days (<30), months (up to 365) or years. */
    private fun duration(days: Long): String {
        val value = days.coerceAtLeast(0)
        return when {
            value > ScoreWeights.DAYS_PER_YEAR -> pluralize(value / ScoreWeights.DAYS_PER_YEAR, "year", "years")
            value >= ScoreWeights.DAYS_PER_MONTH -> pluralize(value / ScoreWeights.DAYS_PER_MONTH, "month", "months")
            else -> pluralize(value, "day", "days")
        }
    }

    private fun pluralize(n: Long, singular: String, plural: String) = "$n ${if (n == 1L) singular else plural}"

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
