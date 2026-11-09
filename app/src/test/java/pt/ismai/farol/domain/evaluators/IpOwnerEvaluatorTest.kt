package pt.ismai.farol.domain.evaluators

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.ismai.farol.domain.model.CheckState
import pt.ismai.farol.domain.model.CheckId

class IpOwnerEvaluatorTest {

    @Test
    fun withNameIsOkWithoutWeight() {
        val v = IpOwnerEvaluator.evaluate("Google LLC", "US")
        assertEquals(CheckId.IP_OWNER, v.id)
        assertEquals(CheckState.OK, v.state)
        assertEquals("Google LLC (US)", v.summary)
        assertEquals(0, v.weight)
        assertEquals(0, v.points)
    }

    @Test
    fun noCountry() {
        assertEquals("Cloudflare", IpOwnerEvaluator.evaluate("Cloudflare", null).summary)
    }

    @Test
    fun noNameIsUnavailable() {
        assertEquals(CheckState.UNAVAILABLE, IpOwnerEvaluator.evaluate(null, "PT").state)
        assertEquals(CheckState.UNAVAILABLE, IpOwnerEvaluator.evaluate("  ", null).state)
    }
}
