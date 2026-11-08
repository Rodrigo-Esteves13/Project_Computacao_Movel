package pt.ismai.farol.domain

import pt.ismai.farol.domain.model.CheckState
import pt.ismai.farol.domain.model.AnalysisResult
import pt.ismai.farol.domain.model.TargetType
import pt.ismai.farol.domain.model.Check
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DATE_FORMAT = "dd/MM/yyyy HH:mm"
private const val LIMITATION_NOTICE = "A high score does not guarantee that a site is legitimate. " +
    "Passive analysis using public data only."

/** Plain text for the Share menu. */
fun reportText(r: AnalysisResult): String {
    val date = SimpleDateFormat(DATE_FORMAT, Locale.forLanguageTag("pt-PT")).format(Date(r.date))
    val type = when (r.type) {
        TargetType.DOMAIN -> "domain"
        TargetType.IP -> "IP"
    }
    val score = r.score?.let { "$it/${ScoreWeights.SCALE}" } ?: "not enough data"
    return buildString {
        appendLine("Farol - security report")
        appendLine("Target: ${r.target} ($type)")
        appendLine("Date: $date")
        appendLine("Score: $score")
        appendLine()
        r.checks.forEach { appendLine(line(it)) }
        appendLine()
        append(LIMITATION_NOTICE)
    }
}

private fun line(v: Check): String = "- ${v.title}: ${label(v.state)} - ${v.summary}"

private fun label(state: CheckState): String = when (state) {
    CheckState.OK -> "Good"
    CheckState.WARNING -> "Warning"
    CheckState.FAIL -> "Critical"
    CheckState.UNAVAILABLE -> "Unavailable"
}
