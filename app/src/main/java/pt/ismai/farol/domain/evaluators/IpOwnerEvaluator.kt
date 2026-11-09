package pt.ismai.farol.domain.evaluators

import pt.ismai.farol.domain.ScoreWeights
import pt.ismai.farol.domain.Checks
import pt.ismai.farol.domain.model.CheckState
import pt.ismai.farol.domain.model.CheckId
import pt.ismai.farol.domain.model.Check

/** Informative: weight 0, never changes the score. OK if a name exists, otherwise UNAVAILABLE. */
object IpOwnerEvaluator {
    private const val ID = CheckId.IP_OWNER

    private const val EXPLANATION = "Public information from the IP address registry (RDAP). " +
        "It says which organization manages this address; it does not count toward the score."
    private const val NO_OWNER = "The public registry does not say who manages this address."

    fun evaluate(name: String?, country: String?): Check {
        if (name.isNullOrBlank()) return Checks.unavailable(ID, NO_OWNER)
        val owner = name.trim()
        val summary = if (country.isNullOrBlank()) owner else "$owner (${country.trim()})"
        val location = if (country.isNullOrBlank()) "" else " Country: ${country.trim()}."
        return Check(
            id = ID,
            title = Checks.title(ID),
            state = CheckState.OK,
            summary = summary,
            detail = "Managed by $owner.$location\n\n$EXPLANATION",
            points = 0,
            weight = ScoreWeights.IP_OWNER,
        )
    }
}
