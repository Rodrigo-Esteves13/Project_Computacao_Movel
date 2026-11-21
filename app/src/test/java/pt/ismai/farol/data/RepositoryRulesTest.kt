package pt.ismai.farol.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.ismai.farol.data.repository.CACHE_MS
import pt.ismai.farol.data.repository.favoriteInfoFor
import pt.ismai.farol.data.repository.freshResult
import pt.ismai.farol.domain.model.AnalysisResult
import pt.ismai.farol.domain.model.Check
import pt.ismai.farol.domain.model.CheckId
import pt.ismai.farol.domain.model.CheckState
import pt.ismai.farol.domain.model.TargetType

class RepositoryRulesTest {
    private fun result(target: String, date: Long, score: Int?, state: CheckState = CheckState.OK, id: Long = date) =
        AnalysisResult(
            target = target,
            type = TargetType.DOMAIN,
            checks = listOf(Check(CheckId.EMAIL, "Email", state, "s", "d", 10, 20)),
            score = score,
            date = date,
            id = id,
        )

    @Test fun cache_returnsNewestResultInsideTenMinutes() {
        val all = listOf(result("a.com", 1_000, 50), result("a.com", 2_000, 80), result("b.com", 3_000, 10))
        assertEquals(80, freshResult(all, "a.com", 2_000 + CACHE_MS)?.score)
    }

    @Test fun cache_ignoresOldResults() {
        val all = listOf(result("a.com", 1_000, 50))
        assertNull(freshResult(all, "a.com", 1_000 + CACHE_MS + 1))
    }

    @Test fun cache_ignoresResultsWithNoRealData() {
        val all = listOf(result("a.com", 1_000, null, CheckState.UNAVAILABLE))
        assertNull(freshResult(all, "a.com", 1_001))
    }

    @Test fun cache_unknownTargetIsAMiss() {
        assertNull(freshResult(listOf(result("a.com", 1_000, 50)), "z.com", 1_001))
    }

    @Test fun favorite_withoutAnalysesHasNoScore() {
        val info = favoriteInfoFor("a.com", emptyList())
        assertNull(info.score)
        assertNull(info.lastAnalysisId)
        assertFalse(info.changed)
    }

    @Test fun favorite_keepsLastAndPreviousScore() {
        val all = listOf(result("a.com", 1_000, 50), result("a.com", 2_000, 80), result("b.com", 3_000, 5))
        val info = favoriteInfoFor("a.com", all)
        assertEquals(80, info.score)
        assertEquals(50, info.previousScore)
        assertEquals(2_000L, info.lastAnalysisId)
        assertTrue(info.changed)
    }

    @Test fun favorite_singleAnalysisIsNotAChange() {
        val info = favoriteInfoFor("a.com", listOf(result("a.com", 1_000, 50)))
        assertNull(info.previousScore)
        assertFalse(info.changed)
    }

    @Test fun favorite_namesTheCheckThatChanged() {
        val all = listOf(
            result("a.com", 1_000, 90, CheckState.OK),
            result("a.com", 2_000, 70, CheckState.WARNING),
        )
        assertEquals(listOf("Email"), favoriteInfoFor("a.com", all).changedChecks)
    }

    @Test fun favorite_noChangedChecksWhenStatesAreEqual() {
        val all = listOf(result("a.com", 1_000, 50), result("a.com", 2_000, 50))
        assertTrue(favoriteInfoFor("a.com", all).changedChecks.isEmpty())
        assertTrue(favoriteInfoFor("a.com", all.take(1)).changedChecks.isEmpty())
    }

    @Test fun favorite_sameScoreAndStatesIsNotAChange() {
        val all = listOf(result("a.com", 1_000, 50), result("a.com", 2_000, 50))
        assertFalse(favoriteInfoFor("a.com", all).changed)
    }
}
