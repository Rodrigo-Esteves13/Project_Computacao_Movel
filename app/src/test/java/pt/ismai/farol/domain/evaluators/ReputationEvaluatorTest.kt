package pt.ismai.farol.domain.evaluators

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.ismai.farol.domain.ScoreCalculator
import pt.ismai.farol.domain.model.CheckId
import pt.ismai.farol.domain.model.CheckState

class ReputationEvaluatorTest {
    private fun verify(malicious: Int, suspicious: Int, state: CheckState, points: Int) {
        val check = ReputationEvaluator.evaluate(malicious, suspicious)
        assertEquals(CheckId.REPUTATION, check.id)
        assertEquals(state, check.state)
        assertEquals(points, check.points)
        assertEquals(15, check.weight)
    }

    @Test fun noDetectionsIsMaximum() = verify(0, 0, CheckState.OK, 15)

    @Test fun onlySuspiciousIsMedium() = verify(0, 3, CheckState.WARNING, 8)

    @Test fun oneOrTwoMaliciousIsLowButNotCritical() {
        verify(1, 0, CheckState.WARNING, 3)
        verify(2, 5, CheckState.WARNING, 3)
    }

    @Test fun moreThanTwoMaliciousIsCriticalWithZero() {
        verify(3, 0, CheckState.FAIL, 0)
        verify(40, 2, CheckState.FAIL, 0)
    }

    @Test fun summaryNamesTheNumberOfEngines() {
        assertEquals("Flagged as malicious by 1 engine", ReputationEvaluator.evaluate(1, 0).summary)
        assertEquals("Flagged as malicious by 5 engines", ReputationEvaluator.evaluate(5, 0).summary)
    }

    @Test fun criticalReputationCapsTheScoreAt40() {
        val perfect = listOf(
            CertificateEvaluator.evaluate(true, 90, "R3", null),
            ExposureEvaluator.evaluate(listOf(443), emptyList()),
            ReputationEvaluator.evaluate(9, 0),
        )
        assertEquals(CheckId.REPUTATION, ScoreCalculator.cap(perfect)?.checkId)
        assertTrue(ScoreCalculator.calculate(perfect)!! <= 40)
    }
}
