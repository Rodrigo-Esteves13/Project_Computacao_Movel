package pt.ismai.farol.domain.evaluators

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.ismai.farol.domain.ScoreWeights
import pt.ismai.farol.domain.model.CheckState

class HeadersEvaluatorTest {

    private val hsts = "strict-transport-security" to "max-age=63072000"
    private val csp = "content-security-policy" to "default-src 'self'"
    private val xcto = "x-content-type-options" to "nosniff"
    private val xfo = "x-frame-options" to "DENY"
    private val ref = "referrer-policy" to "no-referrer"

    @Test
    fun allPresentGives20Ok() {
        val v = HeadersEvaluator.evaluate(mapOf(hsts, csp, xcto, xfo, ref))
        assertEquals(20, v.points)
        assertEquals(ScoreWeights.HEADERS, v.weight)
        assertEquals(CheckState.OK, v.state)
    }

    @Test
    fun noneGives0Fail() {
        val v = HeadersEvaluator.evaluate(emptyMap())
        assertEquals(0, v.points)
        assertEquals(CheckState.FAIL, v.state)
        assertTrue(v.detail.contains("Strict-Transport-Security"))
        assertTrue(v.detail.contains("Referrer-Policy"))
    }

    @Test
    fun threshold16IsOk() {
        // 16 is not reachable: the neighbours are 15 (WARNING) and 17 (OK).
        // frame-ancestors in the CSP counts as X-Frame-Options: 6 + 5 + 3 + 3 = 17
        val v = HeadersEvaluator.evaluate(mapOf(hsts, "content-security-policy" to "frame-ancestors 'none'", xcto))
        assertEquals(17, v.points)
        assertEquals(CheckState.OK, v.state)
        assertFalse(v.detail.contains("X-Frame-Options:"))
        assertTrue(v.detail.contains("Referrer-Policy"))
        // without CSP: 6 + 3 + 3 + 3 = 15
        val fifteen = HeadersEvaluator.evaluate(mapOf(hsts, xcto, xfo, ref))
        assertEquals(15, fifteen.points)
        assertEquals(CheckState.WARNING, fifteen.state)
    }

    @Test
    fun threshold8IsWarning() {
        val eight = HeadersEvaluator.evaluate(mapOf(csp, xcto))
        assertEquals(8, eight.points)
        assertEquals(CheckState.WARNING, eight.state)
        val six = HeadersEvaluator.evaluate(mapOf(hsts))
        assertEquals(6, six.points)
        assertEquals(CheckState.FAIL, six.state)
    }

    @Test
    fun emptyValueDoesNotCount() {
        assertEquals(0, HeadersEvaluator.evaluate(mapOf("strict-transport-security" to " ")).points)
    }
}
