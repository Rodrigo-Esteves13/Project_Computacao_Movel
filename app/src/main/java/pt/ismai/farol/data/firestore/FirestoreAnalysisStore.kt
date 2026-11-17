package pt.ismai.farol.data.firestore

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import pt.ismai.farol.data.repository.AnalysisStore
import pt.ismai.farol.domain.model.AnalysisResult

private const val TAG = "FirestoreStore"
private const val USERS = "users"
private const val ANALYSES = "analyses"
private const val FAVORITES = "favorites"
private const val FIELD_ADDED_AT = "addedAt"
private const val FIELD_TARGET = "target"
private const val BATCH_LIMIT = 400

/**
 * History and favorites in Firestore, one private branch per account: users/{uid}/analyses and
 * users/{uid}/favorites (the security rules only let the owner touch it).
 *
 * Firestore keeps a local cache and syncs by itself, so writes are not awaited: a write made
 * offline completes only when the server is reached, and the listeners already show it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class FirestoreAnalysisStore(
    private val db: FirebaseFirestore,
    /** The signed-in account's uid, or null when nobody is signed in. */
    private val uid: Flow<String?>,
    private val now: () -> Long = System::currentTimeMillis,
) : AnalysisStore {

    private fun user(uid: String) = db.collection(USERS).document(uid)
    private fun analysesOf(uid: String) = user(uid).collection(ANALYSES)
    private fun favoritesOf(uid: String) = user(uid).collection(FAVORITES)

    override val analyses: Flow<List<AnalysisResult>> = uid.flatMapLatest { id ->
        if (id == null) {
            flowOf(emptyList())
        } else {
            listen(analysesOf(id)) { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.id.toLongOrNull()?.let { AnalysisMapper.fromMap(it, doc.data.orEmpty()) }
                }
            }
        }
    }

    override val favorites: Flow<List<String>> = uid.flatMapLatest { id ->
        if (id == null) {
            flowOf(emptyList())
        } else {
            listen(favoritesOf(id).orderBy(FIELD_ADDED_AT)) { snapshot ->
                snapshot.documents.mapNotNull { it.getString(FIELD_TARGET) }
            }
        }
    }

    override suspend fun save(result: AnalysisResult): Long {
        val id = result.date
        val owner = uid.first() ?: return id
        analysesOf(owner).document(id.toString())
            .set(AnalysisMapper.toMap(result.copy(id = id)))
            .addOnFailureListener { Log.w(TAG, "Could not save the analysis", it) }
        return id
    }

    override suspend fun delete(id: Long) {
        val owner = uid.first() ?: return
        analysesOf(owner).document(id.toString()).delete()
            .addOnFailureListener { Log.w(TAG, "Could not delete the analysis", it) }
    }

    override suspend fun clear() {
        val owner = uid.first() ?: return
        deleteAll(analysesOf(owner), waitForServer = false)
    }

    /** Nothing to forget: each account reads its own branch, so another sign-in sees other data. */
    override suspend fun reset() = Unit

    override suspend fun setFavorite(target: String, favorite: Boolean) {
        val owner = uid.first() ?: return
        val doc = favoritesOf(owner).document(target)
        val task = if (favorite) {
            doc.set(mapOf(FIELD_TARGET to target, FIELD_ADDED_AT to now()))
        } else {
            doc.delete()
        }
        task.addOnFailureListener { Log.w(TAG, "Could not update the favorite", it) }
    }

    /** Removes everything stored for [uid]; waits for the server so the account can then be deleted. */
    suspend fun deleteAllFor(uid: String) {
        deleteAll(analysesOf(uid), waitForServer = true)
        deleteAll(favoritesOf(uid), waitForServer = true)
        deleteAll(user(uid).collection(SETTINGS), waitForServer = true)
    }

    private suspend fun deleteAll(collection: Query, waitForServer: Boolean) {
        val documents = collection.get().await().documents
        documents.chunked(BATCH_LIMIT).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            val commit = batch.commit()
            if (waitForServer) commit.await()
        }
    }

    /**
     * Live view of a query. A listener error (for example right after sign-out) ends the flow
     * after one empty answer instead of crashing the collector.
     */
    private fun <T> listen(query: Query, transform: (QuerySnapshot) -> List<T>): Flow<List<T>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Listener failed", error)
                trySend(emptyList())
                close()
            } else if (snapshot != null) {
                trySend(transform(snapshot))
            }
        }
        awaitClose { registration.remove() }
    }
}
