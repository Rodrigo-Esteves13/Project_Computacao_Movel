package pt.ismai.farol.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.ismai.farol.domain.evaluators.CertificateEvaluator
import pt.ismai.farol.domain.evaluators.IpOwnerEvaluator
import pt.ismai.farol.domain.evaluators.EmailEvaluator
import pt.ismai.farol.domain.evaluators.ExposureEvaluator
import pt.ismai.farol.domain.evaluators.HeadersEvaluator
import pt.ismai.farol.domain.evaluators.AgeEvaluator
import pt.ismai.farol.domain.model.CheckState
import pt.ismai.farol.domain.model.CheckId
import pt.ismai.farol.domain.model.Check

class ScoreCalculatorTest {

    private fun v(state: CheckState, points: Int, weight: Int) = Check("x", "x", state, "", "", points, weight)

    @Test
    fun emptyListReturnsNull() {
        assertNull(ScoreCalculator.calculate(emptyList()))
    }

    @Test
    fun onlyUnavailableOrZeroWeightReturnsNull() {
        val list = listOf(
            Checks.unavailable(CheckId.AGE, "no data"),
            IpOwnerEvaluator.evaluate("Google LLC", "US"),
        )
        assertNull(ScoreCalculator.calculate(list))
    }

    @Test
    fun everythingAtMaximumGives100() {
        val list = listOf(
            CertificateEvaluator.evaluate(true, 90, "R3", null),
            AgeEvaluator.evaluate(4000),
            HeadersEvaluator.evaluate(
                mapOf(
                    "strict-transport-security" to "max-age=1",
                    "content-security-policy" to "default-src 'self'",
                    "x-content-type-options" to "nosniff",
                    "x-frame-options" to "DENY",
                    "referrer-policy" to "no-referrer",
                ),
            ),
            EmailEvaluator.evaluate("v=spf1 -all", "v=DMARC1; p=reject"),
            ExposureEvaluator.evaluate(listOf(80, 443), emptyList()),
        )
        assertEquals(100, ScoreCalculator.calculate(list))
    }

    @Test
    fun everythingAtZeroGives0() {
        val list = listOf(
            CertificateEvaluator.evaluate(false, null, null, null),
            AgeEvaluator.evaluate(3),
            HeadersEvaluator.evaluate(emptyMap()),
            EmailEvaluator.evaluate(null, null),
            ExposureEvaluator.evaluate(listOf(22), listOf("CVE-2020-0001")),
        )
        assertEquals(0, ScoreCalculator.calculate(list))
    }

    @Test
    fun unavailableDoNotCountTowardWeights() {
        // 25 + 0 (age unavailable) + 20 + 20 + 15 => 80/80 = 100
        val list = listOf(
            v(CheckState.OK, 25, 25),
            Checks.unavailable(CheckId.AGE, "no data"),
            v(CheckState.OK, 20, 20),
            v(CheckState.OK, 20, 20),
            v(CheckState.OK, 15, 15),
        )
        assertEquals(100, ScoreCalculator.calculate(list))
    }

    @Test
    fun unavailableWithWeightSetIsAlsoIgnored() {
        val list = listOf(v(CheckState.OK, 10, 20), v(CheckState.UNAVAILABLE, 0, 25))
        assertEquals(50, ScoreCalculator.calculate(list))
    }

    @Test
    fun normalizesAndRounds() {
        // 15 / 25 = 60
        assertEquals(60, ScoreCalculator.calculate(listOf(v(CheckState.WARNING, 15, 25))))
        // (12 + 5) / (20 + 25) = 37.77 -> 38
        assertEquals(38, ScoreCalculator.calculate(listOf(v(CheckState.WARNING, 12, 20), v(CheckState.WARNING, 5, 25))))
        // 1 / 3 = 33.33 -> 33
        assertEquals(33, ScoreCalculator.calculate(listOf(v(CheckState.WARNING, 1, 3))))
        // 1 / 8 = 12.5 -> 13
        assertEquals(13, ScoreCalculator.calculate(listOf(v(CheckState.WARNING, 1, 8))))
    }

    @Test
    fun ipWithExposureOnly() {
        val list = listOf(
            ExposureEvaluator.evaluate(listOf(53, 443), emptyList()),
            IpOwnerEvaluator.evaluate("Google LLC", "US"),
        )
        // 12 / 15 = 80
        assertEquals(80, ScoreCalculator.calculate(list))
    }
}
