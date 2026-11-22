package pt.ismai.farol.domain.evaluators

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun softFailSpfStillCountsInFull() {
        verify("v=spf1 include:_spf.google.com ~all", "v=DMARC1; p=reject", 20, CheckState.OK)
    }

    @Test
    fun neutralSpfGetsHalfThePoints() {
        verify("v=spf1 ip4:1.2.3.4 ?all", null, 4, CheckState.WARNING)
    }

    @Test
    fun plusAllSpfLetsAnyoneSendSoItGetsNothing() {
        verify("v=spf1 +all", null, 0, CheckState.FAIL)
        verify("v=spf1 all", null, 0, CheckState.FAIL)
        assertTrue(EmailEvaluator.evaluate("v=spf1 +all", null).detail.contains("+all"))
    }

    @Test
    fun spfWithRedirectAndNoAllIsAccepted() {
        verify("v=spf1 redirect=_spf.example.com", null, 8, CheckState.WARNING)
    }

    @Test
    fun dmarcAppliedToPartOfTheMailGetsHalfThePolicyPoints() {
        verify("v=spf1 -all", "v=DMARC1; p=reject; pct=25", 17, CheckState.WARNING)
        assertTrue(EmailEvaluator.evaluate("v=spf1 -all", "v=DMARC1; p=reject; pct=25").detail.contains("pct"))
    }

    @Test
    fun dmarcWithPct100OrNoPctIsFull() {
        verify("v=spf1 -all", "v=DMARC1; p=quarantine; pct=100", 20, CheckState.OK)
        verify("v=spf1 -all", "v=DMARC1; p=reject; pct=abc", 20, CheckState.OK)
    }

    @Test
    fun uppercaseAndSpacesAreAccepted() {
        verify("  V=SPF1 -all", "V=dmarc1; P=REJECT", 20, CheckState.OK)
    }
}
