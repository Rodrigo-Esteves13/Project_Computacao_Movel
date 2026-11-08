package pt.ismai.farol.domain

import pt.ismai.farol.domain.model.CheckState
import pt.ismai.farol.domain.model.CheckId
import pt.ismai.farol.domain.model.Check

/** Canonical titles by id and a shortcut for UNAVAILABLE (weight 0, does not count toward the score). */
object Checks {
    private const val UNAVAILABLE_SUMMARY = "Could not be checked"

    fun title(id: String): String = when (id) {
        CheckId.CERTIFICATE -> "SSL certificate"
        CheckId.AGE -> "Domain age"
        CheckId.HEADERS -> "Security headers"
        CheckId.EMAIL -> "Email anti-spoofing"
        CheckId.EXPOSURE -> "Port exposure"
        CheckId.IP_OWNER -> "IP owner"
        else -> id
    }

    fun unavailable(id: String, reason: String): Check = Check(
        id = id,
        title = title(id),
        state = CheckState.UNAVAILABLE,
        summary = UNAVAILABLE_SUMMARY,
        detail = reason,
        points = 0,
        weight = 0,
    )
}
