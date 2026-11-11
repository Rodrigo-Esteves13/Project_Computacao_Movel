package pt.ismai.farol.data.repository

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pt.ismai.farol.domain.AnalysisRepository
import pt.ismai.farol.domain.Checks
import pt.ismai.farol.domain.ScoreCalculator
import pt.ismai.farol.domain.TargetNormalizer
import pt.ismai.farol.domain.model.AnalysisProgress
import pt.ismai.farol.domain.model.AnalysisResult
import pt.ismai.farol.domain.model.AnalysisSummary
import pt.ismai.farol.domain.model.Check
import pt.ismai.farol.domain.model.CheckId
import pt.ismai.farol.domain.model.ErrorType
import pt.ismai.farol.domain.model.FarolException
import pt.ismai.farol.domain.model.FavoriteInfo
import pt.ismai.farol.domain.model.NetworkResult
import pt.ismai.farol.domain.model.Target
import pt.ismai.farol.domain.model.TargetType
import pt.ismai.farol.util.Connectivity

private const val CACHE_MS = 10 * 60 * 1000L

/** A unit of work: produces the checks for the given [ids]. */
private class Task(val ids: List<String>, val execute: suspend () -> List<Check>)

internal class AnalysisRepositoryImpl(
    private val checkers: Checkers,
    private val connectivity: Connectivity,
    private val texts: DataTexts,
    private val store: AnalysisStore,
    private val now: () -> Long = System::currentTimeMillis,
) : AnalysisRepository {

    override fun analyze(input: String, force: Boolean): Flow<AnalysisProgress> = channelFlow {
        val target = TargetNormalizer.parse(input)
            ?: throw FarolException(ErrorType.INVALID_INPUT)
        val cached = if (force) null else readCache(target)
        if (cached != null) {
            send(cached)
            return@channelFlow
        }
        if (!connectivity.hasInternet()) throw FarolException(ErrorType.NO_INTERNET)
        val tasks = when (target.type) {
            TargetType.DOMAIN -> domainTasks(target.value)
            TargetType.IP -> ipTasks(target.value)
        }
        execute(target, tasks)
    }.flowOn(Dispatchers.IO)

    private suspend fun readCache(target: Target): AnalysisProgress? {
        val r = latestFor(store.analyses.first(), target.value)
            ?.takeIf { now() - it.date <= CACHE_MS && !allUnavailable(it.checks) } ?: return null
        return AnalysisProgress(
            target = r.target, type = r.type, completed = r.checks, pending = emptyList(),
            score = r.score, finished = true, fromCache = true, analysisId = r.id,
        )
    }

    private suspend fun domainTasks(domain: String): List<Task> {
        // Resolving first makes a missing domain abort the whole analysis (DOMAIN_NOT_FOUND).
        val ip = checkers.resolveIpv4(domain)
        return listOf(
            Task(listOf(CheckId.CERTIFICATE, CheckId.HEADERS)) { checkers.certificateAndHeaders(domain) },
            Task(listOf(CheckId.EMAIL)) { listOf(checkers.email(domain)) },
            Task(listOf(CheckId.EXPOSURE)) { listOf(checkers.exposure(ip)) },
        )
    }

    /** The IP owner check arrives with the RDAP phase. */
    private fun ipTasks(ip: String): List<Task> = listOf(
        Task(listOf(CheckId.EXPOSURE)) { listOf(checkers.exposure(ip)) },
    )

    /** Runs the tasks in parallel, emitting progress on each completion; saves at the end. */
    private suspend fun ProducerScope<AnalysisProgress>.execute(target: Target, tasks: List<Task>) {
        val order = tasks.flatMap { it.ids }
        val completed = mutableListOf<Check>()
        val pending = order.toMutableList()
        val mutex = Mutex()
        fun partial(score: Int? = null, finished: Boolean = false, id: Long? = null) = AnalysisProgress(
            target = target.value, type = target.type,
            completed = completed.sortedBy { order.indexOf(it.id) },
            pending = pending.toList(), score = score, finished = finished,
            fromCache = false, analysisId = id,
        )
        send(partial())
        coroutineScope {
            tasks.map { task ->
                async {
                    val result = runGuarded(task)
                    mutex.withLock {
                        completed += result
                        pending.removeAll(result.map { it.id }.toSet())
                        send(partial())
                    }
                }
            }.awaitAll()
        }
        val ordered = completed.sortedBy { order.indexOf(it.id) }
        val score = ScoreCalculator.calculate(ordered)
        val result = AnalysisResult(target.value, target.type, ordered, score, now())
        val id = if (allUnavailable(ordered)) null else store.save(result)
        send(partial(score = score, finished = true, id = id))
    }

    /** FarolException propagates (aborts the analysis); any other failure becomes UNAVAILABLE. */
    private suspend fun runGuarded(task: Task): List<Check> = try {
        task.execute()
    } catch (e: CancellationException) {
        throw e
    } catch (e: FarolException) {
        throw e
    } catch (e: IOException) {
        if (!connectivity.hasInternet()) throw FarolException(ErrorType.NO_INTERNET, e)
        val reason = if (e is SocketTimeoutException || e.message?.contains("timeout", true) == true) {
            texts.timeout
        } else {
            texts.error
        }
        task.ids.map { Checks.unavailable(it, reason) }
    } catch (e: Exception) {
        task.ids.map { Checks.unavailable(it, texts.error) }
    }

    override suspend fun getAnalysis(id: Long): AnalysisResult? =
        store.analyses.first().firstOrNull { it.id == id }

    override fun history(): Flow<List<AnalysisSummary>> = store.analyses.map { list ->
        list.sortedByDescending { it.date }
            .map { AnalysisSummary(it.id, it.target, it.type, it.score, it.date) }
    }

    override suspend fun deleteAnalysis(id: Long) = store.delete(id)

    override suspend fun clearHistory() = store.clear()

    override fun favorites(): Flow<List<FavoriteInfo>> =
        combine(store.favorites, store.analyses) { targets, analyses ->
            targets.map { favoriteInfo(it, analyses) }
        }

    /** Last and previous scores come from the saved analyses of the target. */
    private fun favoriteInfo(target: String, analyses: List<AnalysisResult>): FavoriteInfo {
        val own = analyses.filter { it.target == target }.sortedByDescending { it.date }
        val last = own.firstOrNull()
        val previous = own.getOrNull(1)
        return FavoriteInfo(
            target = target,
            score = last?.score,
            previousScore = previous?.score,
            changed = last != null && resultChanged(previous, last),
            date = last?.date,
            lastAnalysisId = last?.id,
        )
    }

    override fun isFavorite(target: String): Flow<Boolean> {
        val k = key(target)
        return store.favorites.map { k in it }
    }

    override suspend fun toggleFavorite(target: String) {
        val k = key(target)
        store.setFavorite(k, k !in store.favorites.first())
    }

    private fun key(target: String): String = TargetNormalizer.parse(target)?.value ?: target.trim().lowercase()

    override fun recheckFavorites(): Flow<String> = flow {
        for (target in store.favorites.first()) {
            try {
                analyze(target, force = true).collect { }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val serious = e is FarolException &&
                    (e.type == ErrorType.NO_INTERNET || e.type == ErrorType.RATE_LIMITED)
                if (serious) throw e
            }
            emit(target)
        }
    }

    /** Arrives with the public IP phase. */
    override suspend fun myNetwork(): NetworkResult = throw FarolException(ErrorType.UNKNOWN)

    private fun latestFor(all: List<AnalysisResult>, target: String): AnalysisResult? =
        all.filter { it.target == target }.maxByOrNull { it.date }
}
