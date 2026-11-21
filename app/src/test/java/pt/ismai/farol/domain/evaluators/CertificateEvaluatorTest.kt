package pt.ismai.farol.domain.evaluators

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.ismai.farol.domain.ScoreWeights
import pt.ismai.farol.domain.model.CheckState
import pt.ismai.farol.domain.model.CheckId
import pt.ismai.farol.domain.model.Check

class CertificateEvaluatorTest {

    private fun days(d: Long?) = CertificateEvaluator.evaluate(true, d, "Let's Encrypt", null)

    private fun verify(v: Check, state: CheckState, points: Int) {
        assertEquals(CheckId.CERTIFICATE, v.id)
        assertEquals("SSL certificate", v.title)
        assertEquals(ScoreWeights.CERTIFICATE, v.weight)
        assertEquals(state, v.state)
        assertEquals(points, v.points)
        assertTrue(v.summary.isNotBlank() && !v.summary.contains('\n'))
        assertTrue(v.detail.isNotBlank())
    }

    @Test
    fun moreThan30DaysIsOk() {
        verify(days(31), CheckState.OK, 25)
        verify(days(365), CheckState.OK, 25)
    }

    @Test
    fun between8And30DaysIsWarning() {
        verify(days(30), CheckState.WARNING, 15)
        verify(days(8), CheckState.WARNING, 15)
    }

    @Test
    fun upTo7DaysIsWarningWith5() {
        verify(days(7), CheckState.WARNING, 5)
        verify(days(0), CheckState.WARNING, 5)
    }

    @Test
    fun expiredIsFailWith5() {
        verify(days(-1), CheckState.FAIL, 5)
    }

    @Test
    fun noHttpsOrSslErrorIsZero() {
        verify(CertificateEvaluator.evaluate(false, null, null, null), CheckState.FAIL, 0)
        verify(CertificateEvaluator.evaluate(false, 90, null, "PKIX path building failed"), CheckState.FAIL, 0)
        verify(CertificateEvaluator.evaluate(true, 90, null, "Hostname x not verified"), CheckState.FAIL, 0)
    }

    @Test
    fun downgradeToHttp_capsAGoodCertificateAtAWarning() {
        val check = CertificateEvaluator.evaluate(true, 90, "R3", null, downgradedToHttp = true)
        assertEquals(CheckState.WARNING, check.state)
        assertEquals("Redirects to an unencrypted page", check.summary)
        assertEquals(15, check.points)
    }

    @Test
    fun downgradeDoesNotHideAnAlreadyCriticalResult() {
        val check = CertificateEvaluator.evaluate(false, null, null, "PKIX path building failed", downgradedToHttp = true)
        assertEquals(CheckState.FAIL, check.state)
        assertEquals(0, check.points)
    }

    @Test
    fun tlsVersionIsAddedToTheDetail() {
        val check = CertificateEvaluator.evaluate(true, 90, "R3", null, tlsVersion = "TLSv1.3")
        assertTrue(check.detail.contains("Connection protocol: TLSv1.3."))
        assertEquals(CheckState.OK, check.state)
    }

    @Test
    fun siteWithoutHttps_saysSoInsteadOfInvalidCertificate() {
        val check = CertificateEvaluator.evaluate(false, null, null, null)
        assertEquals("No secure connection (HTTPS)", check.summary)
    }

    @Test
    fun errorMessagesAreUnderstandable() {
        val expired = CertificateEvaluator.evaluate(false, null, null, "CertificateExpiredException: NotAfter")
        assertEquals("Certificate expired", expired.summary)
        val name = CertificateEvaluator.evaluate(false, null, null, "Hostname wrong.host.badssl.com not verified")
        assertEquals("The certificate does not match the site", name.summary)
        val selfSigned = CertificateEvaluator.evaluate(false, null, null, "Trust anchor for certification path not found.")
        assertEquals("Certificate not trusted", selfSigned.summary)
    }

    @Test
    fun httpsWithoutDateIsValid() {
        verify(days(null), CheckState.OK, 25)
        assertTrue(days(null).detail.contains("Let's Encrypt"))
    }
}
