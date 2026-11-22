package pt.ismai.farol.domain.evaluators

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.ismai.farol.domain.model.CheckId
import pt.ismai.farol.domain.model.CheckState

class DnsSignalsEvaluatorTest {
    @Test fun everythingOnIsOk() {
        val check = DnsSignalsEvaluator.evaluate(dnssec = true, caa = true, ipv6 = true)
        assertEquals(CheckId.DNS_SIGNALS, check.id)
        assertEquals(CheckState.OK, check.state)
        assertEquals("DNSSEC on, CAA set, IPv6 yes", check.summary)
    }

    @Test fun neverCountsTowardTheScore() {
        val check = DnsSignalsEvaluator.evaluate(true, false, false)
        assertEquals(0, check.weight)
        assertEquals(0, check.points)
    }

    @Test fun oneOfDnssecOrCaaIsEnoughForOk() {
        assertEquals(CheckState.OK, DnsSignalsEvaluator.evaluate(true, false, false).state)
        assertEquals(CheckState.OK, DnsSignalsEvaluator.evaluate(false, true, false).state)
    }

    @Test fun neitherDnssecNorCaaIsAWarning_evenWithIpv6() {
        assertEquals(CheckState.WARNING, DnsSignalsEvaluator.evaluate(false, false, true).state)
    }

    @Test fun unknownSignalsAreLeftOut() {
        val check = DnsSignalsEvaluator.evaluate(dnssec = null, caa = true, ipv6 = null)
        assertEquals("CAA set", check.summary)
        assertTrue(!check.detail.contains("DNSSEC:"))
    }

    @Test fun nothingReadableIsUnavailable() {
        assertEquals(CheckState.UNAVAILABLE, DnsSignalsEvaluator.evaluate(null, null, null).state)
    }
}
