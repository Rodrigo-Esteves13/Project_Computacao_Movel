package pt.ismai.farol.domain.evaluators

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.ismai.farol.domain.ScoreWeights
import pt.ismai.farol.domain.model.CheckState

class ExposureEvaluatorTest {

    private fun verify(ports: List<Int>, cves: List<String>, points: Int, state: CheckState) {
        val v = ExposureEvaluator.evaluate(ports, cves)
        assertEquals(ScoreWeights.EXPOSURE, v.weight)
        assertEquals(points, v.points)
        assertEquals(state, v.state)
    }

    @Test
    fun noDataGives15() {
        verify(emptyList(), emptyList(), 15, CheckState.OK)
    }

    @Test
    fun webPortsOnlyGive15() {
        verify(listOf(80, 443), emptyList(), 15, CheckState.OK)
    }

    @Test
    fun eachExtraPortTakes3() {
        verify(listOf(22), emptyList(), 12, CheckState.WARNING)
        verify(listOf(53, 443), emptyList(), 12, CheckState.WARNING)
        verify(listOf(21, 22, 25, 80, 443), emptyList(), 6, CheckState.WARNING)
        verify(listOf(21, 22, 23, 25), emptyList(), 3, CheckState.WARNING)
    }

    @Test
    fun minimumZero() {
        verify(listOf(21, 22, 23, 25, 110), emptyList(), 0, CheckState.FAIL)
        verify(listOf(21, 22, 23, 25, 110, 3306, 5432), emptyList(), 0, CheckState.FAIL)
    }

    @Test
    fun repeatedPortsCountOnce() {
        verify(listOf(22, 22), emptyList(), 12, CheckState.WARNING)
    }

    @Test
    fun anyCveGives0() {
        verify(listOf(443), listOf("CVE-2021-44228"), 0, CheckState.FAIL)
        verify(emptyList(), listOf("CVE-2021-44228"), 0, CheckState.FAIL)
    }
}
