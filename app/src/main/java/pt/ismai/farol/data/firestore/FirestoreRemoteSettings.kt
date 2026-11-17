package pt.ismai.farol.data.firestore

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import pt.ismai.farol.data.repository.RemoteSettings
import pt.ismai.farol.data.repository.SyncedSettings

private const val TAG = "FirestoreSettings"
private const val USERS = "users"
internal const val SETTINGS = "settings"
private const val PREFERENCES = "preferences"

/**
 * The settings that follow the account (theme and timeout), in users/{uid}/settings/preferences.
 * The security rules already cover this path: only the owner reads and writes it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class FirestoreRemoteSettings(
    private val db: FirebaseFirestore,
    /** The signed-in account's uid, or null when nobody is signed in. */
    private val uid: Flow<String?>,
) : RemoteSettings {

    private fun document(uid: String) = db.collection(USERS).document(uid).collection(SETTINGS).document(PREFERENCES)

    override fun observe(): Flow<SyncedSettings?> = uid.flatMapLatest { id ->
        if (id == null) emptyFlow() else callbackFlow {
            val registration = document(id).addSnapshotListener { snapshot, error ->
                when {
                    error != null -> {
                        Log.w(TAG, "Listener failed", error)
                        close()
                    }
                    snapshot == null -> Unit
                    snapshot.exists() -> trySend(SettingsMapper.fromMap(snapshot.data.orEmpty()))
                    // "Missing" only counts when the server said so, not when the local cache is still empty.
                    !snapshot.metadata.isFromCache -> trySend(null)
                }
            }
            awaitClose { registration.remove() }
        }
    }

    /** Not awaited: offline, Firestore keeps the write and sends it when the network returns. */
    override suspend fun save(settings: SyncedSettings) {
        val owner = uid.first() ?: return
        document(owner).set(SettingsMapper.toMap(settings), SetOptions.merge())
            .addOnFailureListener { Log.w(TAG, "Could not save the settings", it) }
    }
}
