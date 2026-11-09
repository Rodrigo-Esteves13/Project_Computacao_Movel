package pt.ismai.farol.domain.evaluators

import pt.ismai.farol.domain.ScoreWeights
import pt.ismai.farol.domain.Checks
import pt.ismai.farol.domain.model.CheckState
import pt.ismai.farol.domain.model.CheckId
import pt.ismai.farol.domain.model.Check

/**
 * Weight 15. Empty ports/cves = "no known ports" (InternetDB 404) -> 15 points.
 * -3 for each port beyond 80/443 (min. 0); any CVE = 0 FAIL.
 */
object ExposureEvaluator {
    private const val ID = CheckId.EXPOSURE
    private const val WEIGHT = ScoreWeights.EXPOSURE

    private const val EXPLANATION = "This data comes from a public database (Shodan InternetDB); the app does not " +
        "run scans. Every open port is a possible entry point. Ports 80 and 443 are the normal " +
        "ones for a website."

    fun evaluate(ports: List<Int>, cves: List<String>): Check {
        val openPorts = ports.distinct().sorted()
        val extra = openPorts.filterNot { it in ScoreWeights.WEB_PORTS }
        val vulns = cves.distinct()
        if (vulns.isNotEmpty()) return withVulnerabilities(openPorts, vulns)
        val points = (WEIGHT - extra.size * ScoreWeights.EXPOSURE_PORT_PENALTY).coerceAtLeast(0)
        val summary = when {
            openPorts.isEmpty() -> "No known open ports"
            extra.isEmpty() -> "Only the normal website ports"
            extra.size == 1 -> "1 open port beyond the website ports"
            else -> "${extra.size} open ports beyond the website ports"
        }
        return create(stateForPoints(points, WEIGHT), points, summary, portList(openPorts))
    }

    private fun withVulnerabilities(openPorts: List<Int>, vulns: List<String>): Check {
        val summary = if (vulns.size == 1) "1 known security flaw" else "${vulns.size} known security flaws"
        val text = "Known flaws (CVE) associated with this address: ${vulns.joinToString(", ")}. " +
            "Outdated software with public flaws is an easy target.\n" + portList(openPorts)
        return create(CheckState.FAIL, 0, summary, text)
    }

    private fun portList(openPorts: List<Int>): String =
        if (openPorts.isEmpty()) {
            "There is no record of open ports."
        } else {
            "Open ports: ${openPorts.joinToString(", ")}."
        }

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
