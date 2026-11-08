package pt.ismai.farol.domain

import kotlinx.coroutines.flow.Flow
import pt.ismai.farol.domain.model.AnalysisSummary
import pt.ismai.farol.domain.model.FavoriteInfo
import pt.ismai.farol.domain.model.AnalysisProgress
import pt.ismai.farol.domain.model.AnalysisResult
import pt.ismai.farol.domain.model.NetworkResult
import pt.ismai.farol.domain.model.ThemePref

/** Contract between UI and data. Implementation: data/repository/AnalysisRepositoryImpl. */
interface AnalysisRepository {
    /**
     * Validates/normalizes [input]; throws FarolException(INVALID_INPUT) if invalid.
     * Emits [AnalysisProgress] as each check arrives (checks run in parallel).
     * If an analysis of the same target exists that is < 10 min old and [force] == false, emits a single
     * finished progress with fromCache = true. Saves the finished analysis in the DB.
     * Serious errors (no internet, domain not found, 429) -> FarolException in the Flow.
     */
    fun analyze(input: String, force: Boolean = false): Flow<AnalysisProgress>

    suspend fun getAnalysis(id: Long): AnalysisResult?

    fun history(): Flow<List<AnalysisSummary>>
    suspend fun deleteAnalysis(id: Long)
    suspend fun clearHistory()

    fun favorites(): Flow<List<FavoriteInfo>>
    fun isFavorite(target: String): Flow<Boolean>
    suspend fun toggleFavorite(target: String)

    /** Rechecks SEQUENTIALLY. Emits the target that was just rechecked. */
    fun recheckFavorites(): Flow<String>

    /** Public IP (ipify) + exposure (InternetDB). */
    suspend fun myNetwork(): NetworkResult
}

interface SettingsRepository {
    val theme: Flow<ThemePref>
    val timeoutSeconds: Flow<Int>
    suspend fun setTheme(theme: ThemePref)
    suspend fun setTimeout(seconds: Int)
}
