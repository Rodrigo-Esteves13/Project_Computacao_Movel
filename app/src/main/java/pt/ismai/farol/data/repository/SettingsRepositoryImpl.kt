package pt.ismai.farol.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import pt.ismai.farol.domain.SettingsRepository
import pt.ismai.farol.domain.model.ThemePref

private const val TIMEOUT_DEFAULT_S = 10
internal const val TIMEOUT_MIN_S = 1
internal const val TIMEOUT_MAX_S = 120

internal val Context.farolDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** The settings that follow the account. A null field means "not set". */
data class SyncedSettings(val theme: ThemePref?, val timeoutSeconds: Int?)

/** The account's copy of [SyncedSettings] in the cloud. */
internal interface RemoteSettings {
    /** The saved settings; null when the account has none yet; nothing while nobody is signed in. */
    fun observe(): Flow<SyncedSettings?>

    suspend fun save(settings: SyncedSettings)
}

/**
 * Settings live in DataStore on the device, so they work offline, before sign-in and at start-up
 * (the theme is needed before the first screen). Theme and timeout are also kept in the account
 * when [remote] is given, so they follow the user to another device. The daily check stays on the
 * device on purpose: the background job and the notification permission belong to each phone.
 */
class SettingsRepositoryImpl internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val remote: RemoteSettings?,
) : SettingsRepository {
    constructor(dataStore: DataStore<Preferences>) : this(dataStore, null)

    private val themeKey = stringPreferencesKey("theme")
    private val timeoutKey = intPreferencesKey("timeout_s")
    private val dailyCheckKey = booleanPreferencesKey("daily_check")

    private val prefs: Flow<Preferences> = dataStore.data.catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }

    override val theme: Flow<ThemePref> = prefs.map { it.currentTheme() }

    override val timeoutSeconds: Flow<Int> = prefs.map { it.currentTimeout() }

    override val dailyCheck: Flow<Boolean> = prefs.map { it[dailyCheckKey] ?: false }

    override suspend fun setTheme(theme: ThemePref) {
        dataStore.edit { it[themeKey] = theme.name }
        remote?.save(SyncedSettings(theme, null))
    }

    override suspend fun setTimeout(seconds: Int) {
        val value = seconds.coerceIn(TIMEOUT_MIN_S, TIMEOUT_MAX_S)
        dataStore.edit { it[timeoutKey] = value }
        remote?.save(SyncedSettings(null, value))
    }

    override suspend fun setDailyCheck(enabled: Boolean) {
        dataStore.edit { it[dailyCheckKey] = enabled }
    }

    /**
     * Keeps the device and the account in step; runs for as long as the app lives. The account's
     * copy wins when it exists (another device may have changed it). A new account has none, so it
     * starts from this device's settings.
     */
    suspend fun sync() {
        val cloud = remote ?: return
        cloud.observe().collect { saved ->
            if (saved == null) {
                val current = prefs.first()
                cloud.save(SyncedSettings(current.currentTheme(), current.currentTimeout()))
            } else {
                apply(saved)
            }
        }
    }

    private suspend fun apply(saved: SyncedSettings) {
        dataStore.edit { p ->
            // Only real changes are written, so the echo of this device's own save does nothing.
            saved.theme?.takeIf { it != p.currentTheme() }?.let { p[themeKey] = it.name }
            saved.timeoutSeconds?.takeIf { it != p.currentTimeout() }?.let { p[timeoutKey] = it }
        }
    }

    private fun Preferences.currentTheme(): ThemePref =
        this[themeKey]?.let { name -> ThemePref.entries.firstOrNull { it.name == name } } ?: ThemePref.SYSTEM

    private fun Preferences.currentTimeout(): Int =
        (this[timeoutKey] ?: TIMEOUT_DEFAULT_S).coerceIn(TIMEOUT_MIN_S, TIMEOUT_MAX_S)
}
