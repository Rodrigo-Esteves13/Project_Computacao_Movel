package pt.ismai.farol.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.ismai.farol.data.firestore.AnalysisMapper
import pt.ismai.farol.domain.model.AnalysisResult
import pt.ismai.farol.domain.model.Check
import pt.ismai.farol.domain.model.CheckId
import pt.ismai.farol.domain.model.CheckState
import pt.ismai.farol.domain.model.TargetType

class AnalysisMapperTest {
    private val result = AnalysisResult(
        target = "github.com",
        type = TargetType.DOMAIN,
        checks = listOf(Check(CheckId.EMAIL, "Email", CheckState.WARNING, "Partial", "Detail", 14, 20)),
        score = 70,
        date = 1_700_000_000_000L,
    )

    @Test fun roundTrip_keepsTheWholeResultAndTakesTheIdFromTheDocument() {
        val map = AnalysisMapper.toMap(result)
        assertEquals(result.copy(id = 42L), AnalysisMapper.fromMap(42L, map))
    }

    @Test fun queryableFields_sitNextToTheJson() {
        val map = AnalysisMapper.toMap(result)
        assertEquals("github.com", map[AnalysisMapper.FIELD_TARGET])
        assertEquals("DOMAIN", map[AnalysisMapper.FIELD_TYPE])
        assertEquals(70, map[AnalysisMapper.FIELD_SCORE])
        assertEquals(1_700_000_000_000L, map[AnalysisMapper.FIELD_DATE])
    }

    @Test fun unreadableDocuments_areSkipped() {
        assertNull(AnalysisMapper.fromMap(1L, emptyMap()))
        assertNull(AnalysisMapper.fromMap(1L, mapOf(AnalysisMapper.FIELD_RESULT_JSON to "not json")))
    }

    @Test fun resultWithoutScore_roundTrips() {
        val noScore = result.copy(score = null)
        assertEquals(noScore.copy(id = 7L), AnalysisMapper.fromMap(7L, AnalysisMapper.toMap(noScore)))
    }
}
