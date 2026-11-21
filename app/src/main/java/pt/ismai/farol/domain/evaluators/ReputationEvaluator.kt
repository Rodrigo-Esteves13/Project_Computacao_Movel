package pt.ismai.farol.domain.evaluators

import pt.ismai.farol.domain.Checks
import pt.ismai.farol.domain.ScoreWeights
import pt.ismai.farol.domain.model.Check
import pt.ismai.farol.domain.model.CheckId
import pt.ismai.farol.domain.model.CheckState

/**
 * Weight 15. From the counts of the VirusTotal security engines: no detections = 15 OK;
 * only "suspicious" = 8 WARNING; 1 or 2 "malicious" = 3 WARNING; more than 2 = 0 FAIL.
 * One or two engines can be a false alarm, so only a larger number is critical.
 */
object ReputationEvaluator {
    private const val ID = CheckId.REPUTATION
    private const val WEIGHT = ScoreWeights.REPUTATION

    private const val EXPLANATION = "VirusTotal asks dozens of security engines whether they know the " +
        "domain as malicious. Detections are a strong warning; no detections do not prove the site is safe."

    fun evaluate(malicious: Int, suspicious: Int): Check = when {
        malicious > ScoreWeights.REPUTATION_MANY_MALICIOUS -> create(
            CheckState.FAIL, 0, "Flagged as malicious by $malicious engines",
            "Several security engines classify this domain as malicious.",
        )
        malicious > 0 -> create(
            CheckState.WARNING, ScoreWeights.REPUTATION_POINTS_FEW_MALICIOUS,
            "Flagged as malicious by ${engines(malicious)}",
            "Few engines flag it, which can be a false alarm, but it deserves caution.",
        )
        suspicious > 0 -> create(
            CheckState.WARNING, ScoreWeights.REPUTATION_POINTS_SUSPICIOUS,
            "Seen as suspicious by ${engines(suspicious)}",
            "No engine calls it malicious, but some find it suspicious.",
        )
        else -> create(CheckState.OK, WEIGHT, "No threats reported", "No engine flagged this domain.")
    }

    private fun engines(n: Int) = "$n ${if (n == 1) "engine" else "engines"}"

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
