package pt.ismai.farol.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.ismai.farol.domain.model.FavoriteInfo

class FavoriteAlertsTest {
    private fun fav(target: String, score: Int?, previous: Int?, changed: Boolean, date: Long?) =
        FavoriteInfo(target, score, previous, changed, date, lastAnalysisId = date)

    @Test fun onlyFreshChangesAreReported() {
        val list = listOf(
            fav("new.com", 60, 80, changed = true, date = 2_000),
            fav("old.com", 60, 80, changed = true, date = 500), // changed in an earlier run
            fav("same.com", 70, 70, changed = false, date = 2_000),
            fav("never.com", null, null, changed = false, date = null),
        )
        assertEquals(listOf("new.com"), FavoriteAlerts.changedSince(list, 1_000).map { it.target })
    }

    @Test fun singleChangeShowsBothScores() {
        val text = FavoriteAlerts.message(listOf(fav("a.com", 60, 80, true, 1)))
        assertEquals("a.com: score went from 80 to 60", text)
    }

    @Test fun changeWithSameScoreSaysACheckChanged() {
        val text = FavoriteAlerts.message(listOf(fav("a.com", 70, 70, true, 1)))
        assertEquals("a.com: one of the checks changed", text)
    }

    @Test fun manyChangesAreSummarized() {
        val list = listOf("a.com", "b.com", "c.com", "d.com", "e.com").map { fav(it, 1, 2, true, 1) }
        val text = FavoriteAlerts.message(list)
        assertEquals("5 favorites changed: a.com, b.com, c.com and 2 more", text)
        assertTrue(FavoriteAlerts.message(list.take(2)).startsWith("2 favorites changed: a.com, b.com"))
    }
}
