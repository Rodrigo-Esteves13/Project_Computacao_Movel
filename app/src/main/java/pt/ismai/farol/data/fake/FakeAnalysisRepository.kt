package pt.ismai.farol.data.fake

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import pt.ismai.farol.domain.AnalysisRepository
import pt.ismai.farol.domain.Checks
import pt.ismai.farol.domain.TargetNormalizer
import pt.ismai.farol.domain.model.AnalysisProgress
import pt.ismai.farol.domain.model.AnalysisResult
import pt.ismai.farol.domain.model.AnalysisSummary
import pt.ismai.farol.domain.model.Check
import pt.ismai.farol.domain.model.CheckId
import pt.ismai.farol.domain.model.CheckState
import pt.ismai.farol.domain.model.ErrorType
import pt.ismai.farol.domain.model.FarolException
import pt.ismai.farol.domain.model.FavoriteInfo
import pt.ismai.farol.domain.model.NetworkResult
import pt.ismai.farol.domain.model.TargetType
import kotlin.math.abs

/**
 * Phase 2 stand-in for the real repository: everything is in memory and the data is made up,
 * so the screens can be built and tested without any network. Replaced by AnalysisRepositoryImpl later.
 */
class FakeAnalysisRepository : AnalysisRepository {

    private val analyses = MutableStateFlow<List<AnalysisResult>>(emptyList())
    private val favoriteTargets = MutableStateFlow<List<String>>(emptyList())
    private var nextId = 1L

    override fun analyze(input: String, force: Boolean): Flow<AnalysisProgress> = flow {
        val target = TargetNormalizer.parse(input) ?: throw FarolException(ErrorType.INVALID_INPUT)
        if (target.value.startsWith("doesnotexist")) throw FarolException(ErrorType.DOMAIN_NOT_FOUND)

        val cached = analyses.value.firstOrNull {
            it.target == target.value && System.currentTimeMillis() - it.date < CACHE_MILLIS
        }
        if (cached != null && !force) {
            emit(finished(cached, fromCache = true))
            return@flow
        }

        val checks = fakeChecks(target.value, target.type)
        val done = mutableListOf<Check>()
        emit(progress(target.value, target.type, done, checks.map { it.id }))
        for (check in checks) {
            delay(STEP_DELAY_MILLIS)
            done += check
            emit(progress(target.value, target.type, done, checks.drop(done.size).map { it.id }))
        }
        val result = AnalysisResult(
            target = target.value,
            type = target.type,
            checks = checks,
            score = fakeScore(target.value),
            date = System.currentTimeMillis(),
            id = nextId++,
        )
        analyses.value = analyses.value + result
        emit(finished(result, fromCache = false))
    }

    override suspend fun getAnalysis(id: Long): AnalysisResult? = analyses.value.firstOrNull { it.id == id }

    override fun history(): Flow<List<AnalysisSummary>> = analyses.map { list ->
        list.sortedByDescending { it.date }.map { AnalysisSummary(it.id, it.target, it.type, it.score, it.date) }
    }

    override suspend fun deleteAnalysis(id: Long) {
        analyses.value = analyses.value.filterNot { it.id == id }
    }

    override suspend fun clearHistory() {
        analyses.value = emptyList()
    }

    override fun favorites(): Flow<List<FavoriteInfo>> = combine(favoriteTargets, analyses) { targets, results ->
        targets.map { target ->
            val last = results.filter { it.target == target }.maxByOrNull { it.date }
            FavoriteInfo(target, last?.score, previousScore = null, changed = false, date = last?.date, lastAnalysisId = last?.id)
        }
    }

    override fun isFavorite(target: String): Flow<Boolean> = favoriteTargets.map { target in it }

    override suspend fun toggleFavorite(target: String) {
        val current = favoriteTargets.value
        favoriteTargets.value = if (target in current) current - target else current + target
    }

    override fun recheckFavorites(): Flow<String> = flow {
        for (target in favoriteTargets.value) {
            delay(STEP_DELAY_MILLIS)
            emit(target)
        }
    }

    override suspend fun myNetwork(): NetworkResult {
        delay(STEP_DELAY_MILLIS)
        return NetworkResult(
            publicIp = DEMO_PUBLIC_IP,
            connectionType = "Wi-Fi",
            ports = listOf(443),
            cves = emptyList(),
            exposure = check(CheckId.EXPOSURE, CheckState.OK, "Only the normal ports of a website", 15, 15),
        )
    }

    private fun progress(target: String, type: TargetType, done: List<Check>, pending: List<String>) =
        AnalysisProgress(target, type, done.toList(), pending, null, false, false, null)

    private fun finished(result: AnalysisResult, fromCache: Boolean) = AnalysisProgress(
        result.target, result.type, result.checks, emptyList(), result.score, true, fromCache, result.id,
    )

    private fun fakeScore(target: String): Int = 40 + abs(target.hashCode()) % 60

    private fun fakeChecks(target: String, type: TargetType): List<Check> {
        val exposure = check(CheckId.EXPOSURE, CheckState.OK, "Only the normal ports of a website", 15, 15)
        if (type == TargetType.IP) {
            return listOf(
                exposure,
                check(CheckId.IP_OWNER, CheckState.OK, "Registered to a demo organization", 0, 0),
            )
        }
        return listOf(
            check(CheckId.CERTIFICATE, CheckState.OK, "Valid, expires in 88 days", 25, 25),
            check(CheckId.HEADERS, CheckState.WARNING, "3 of 5 protections active", 12, 20),
            Checks.unavailable(CheckId.AGE, "Could not be verified"),
            check(CheckId.EMAIL, CheckState.WARNING, "Partial protection against fake emails", 14, 20),
            exposure,
        )
    }

    private fun check(id: String, state: CheckState, summary: String, points: Int, weight: Int) = Check(
        id = id,
        title = Checks.title(id),
        state = state,
        summary = summary,
        detail = "$summary. This is demo data shown while the real checks are not connected yet.",
        points = points,
        weight = weight,
    )

    private companion object {
        const val CACHE_MILLIS = 10 * 60 * 1000L
        const val STEP_DELAY_MILLIS = 600L
        const val DEMO_PUBLIC_IP = "203.0.113.7"
    }
}
