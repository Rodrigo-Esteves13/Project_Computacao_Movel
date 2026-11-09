package pt.ismai.farol.domain.evaluators

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.ismai.farol.domain.ScoreWeights
import pt.ismai.farol.domain.model.CheckState

class EmailEvaluatorTest {

    private fun verify(spf: String?, dmarc: String?, points: Int, state: CheckState) {
        val v = EmailEvaluator.evaluate(spf, dmarc)
        assertEquals(ScoreWeights.EMAIL, v.weight)
        assertEquals(points, v.points)
        assertEquals(state, v.state)
    }

    @Test
    fun spfAndDmarcRejectGives20() {
        verify("v=spf1 include:_spf.google.com ~all", "v=DMARC1; p=reject; pct=100", 20, CheckState.OK)
        verify("v=spf1 -all", "v=DMARC1;p=quarantine", 20, CheckState.OK)
    }

    @Test
    fun dmarcMonitoringOnlyGives14() {
        verify("v=spf1 -all", "v=DMARC1; p=none; sp=reject", 14, CheckState.WARNING)
    }

    @Test
    fun spfOnlyGives8() {
        verify("v=spf1 -all", null, 8, CheckState.WARNING)
    }

    @Test
    fun dmarcRejectOnlyGives12() {
        verify(null, "v=DMARC1; p=reject", 12, CheckState.WARNING)
    }

    @Test
    fun nothingGives0() {
        verify(null, null, 0, CheckState.FAIL)
        verify("google-site-verification=abc", "some text", 0, CheckState.FAIL)
    }

    @Test
    fun uppercaseAndSpacesAreAccepted() {
        verify("  V=SPF1 -all", "V=dmarc1; P=REJECT", 20, CheckState.OK)
    }
}
