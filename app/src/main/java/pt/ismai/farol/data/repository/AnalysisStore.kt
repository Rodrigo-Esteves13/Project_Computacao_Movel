package pt.ismai.farol.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import pt.ismai.farol.domain.model.AnalysisResult

/**
 * Where finished analyses and favorite targets are kept. The repository derives everything else
 * (history lines, favorite scores, cache) from these two collections.
 * [InMemoryAnalysisStore] is the stand-in used until the cloud store replaces it.
 */
interface AnalysisStore {
    /** Every saved analysis, in no particular order. */
    val analyses: Flow<List<AnalysisResult>>

    /** Favorite targets, in the order they were added. */
    val favorites: Flow<List<String>>

    /** Stores [result] and returns the id it was saved under. */
    suspend fun save(result: AnalysisResult): Long

    suspend fun delete(id: Long)
    suspend fun clear()
    suspend fun setFavorite(target: String, favorite: Boolean)
}

class InMemoryAnalysisStore : AnalysisStore {
    private val items = MutableStateFlow<List<AnalysisResult>>(emptyList())
    private val favoriteList = MutableStateFlow<List<String>>(emptyList())
    private var nextId = 1L

    override val analyses: Flow<List<AnalysisResult>> = items
    override val favorites: Flow<List<String>> = favoriteList

    override suspend fun save(result: AnalysisResult): Long {
        val id = synchronized(this) { nextId++ }
        items.update { it + result.copy(id = id) }
        return id
    }

    override suspend fun delete(id: Long) = items.update { list -> list.filterNot { it.id == id } }

    override suspend fun clear() {
        items.value = emptyList()
    }

    override suspend fun setFavorite(target: String, favorite: Boolean) = favoriteList.update { list ->
        if (favorite) (list - target) + target else list - target
    }
}
