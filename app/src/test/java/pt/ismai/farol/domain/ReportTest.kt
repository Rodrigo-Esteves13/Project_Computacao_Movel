package pt.ismai.farol.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import pt.ismai.farol.domain.evaluators.EmailEvaluator
import pt.ismai.farol.domain.model.CheckId
import pt.ismai.farol.domain.model.AnalysisResult
import pt.ismai.farol.domain.model.TargetType

class ReportTest {

    @Test
    fun includesTargetScoreLinesAndNotice() {
        val r = AnalysisResult(
            target = "github.com",
            type = TargetType.DOMAIN,
            checks = listOf(
                EmailEvaluator.evaluate("v=spf1 -all", "v=DMARC1; p=reject"),
                Checks.unavailable(CheckId.AGE, "no data"),
            ),
            score = 100,
            date = 0L,
        )
        val text = reportText(r)
        assertTrue(text.contains("Target: github.com (domain)"))
        assertTrue(text.contains("Score: 100/100"))
        assertTrue(text.contains("- Email anti-spoofing: Good"))
        assertTrue(text.contains("- Domain age: Unavailable"))
        assertTrue(text.contains("A high score does not guarantee that a site is legitimate."))
    }

    @Test
    fun noScore() {
        val r = AnalysisResult("8.8.8.8", TargetType.IP, emptyList(), null, 0L)
        assertTrue(reportText(r).contains("Score: not enough data"))
    }
}
