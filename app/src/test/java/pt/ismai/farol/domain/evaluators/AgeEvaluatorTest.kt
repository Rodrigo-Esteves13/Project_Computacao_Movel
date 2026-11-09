package pt.ismai.farol.domain.evaluators

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.ismai.farol.domain.ScoreWeights
import pt.ismai.farol.domain.model.CheckState
import pt.ismai.farol.domain.model.CheckId

class AgeEvaluatorTest {

    private fun verify(days: Long, state: CheckState, points: Int) {
        val v = AgeEvaluator.evaluate(days)
        assertEquals(CheckId.AGE, v.id)
        assertEquals(ScoreWeights.DOMAIN_AGE, v.weight)
        assertEquals("days=$days", state, v.state)
        assertEquals("days=$days", points, v.points)
    }

    @Test
    fun thresholds() {
        verify(366, CheckState.OK, 20)
        verify(365, CheckState.WARNING, 12)
        verify(90, CheckState.WARNING, 12)
        verify(89, CheckState.WARNING, 6)
        verify(30, CheckState.WARNING, 6)
        verify(29, CheckState.FAIL, 0)
        verify(0, CheckState.FAIL, 0)
    }

    @Test
    fun noDateIsUnavailable() {
        val v = AgeEvaluator.evaluate(null)
        assertEquals(CheckState.UNAVAILABLE, v.state)
        assertEquals(0, v.weight)
        assertEquals(0, v.points)
        assertEquals("Domain age", v.title)
    }

    @Test
    fun readableSummary() {
        assertEquals("Registered 3 years ago", AgeEvaluator.evaluate(1100).summary)
        assertEquals("Registered 1 month ago", AgeEvaluator.evaluate(45).summary)
        assertEquals("Registered 12 months ago", AgeEvaluator.evaluate(365).summary)
        assertEquals("Very recent: registered 1 day ago", AgeEvaluator.evaluate(1).summary)
    }
}
