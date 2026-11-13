package pt.ismai.farol.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import pt.ismai.farol.domain.SettingsRepository
import pt.ismai.farol.domain.model.ThemePref

private const val TIMEOUT_DEFAULT_S = 10
private const val TIMEOUT_MIN_S = 1
private const val TIMEOUT_MAX_S = 120

internal val Context.farolDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepositoryImpl(private val dataStore: DataStore<Preferences>) : SettingsRepository {
    private val themeKey = stringPreferencesKey("theme")
    private val timeoutKey = intPreferencesKey("timeout_s")

    private val prefs: Flow<Preferences> = dataStore.data.catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }

    override val theme: Flow<ThemePref> = prefs.map { p ->
        p[themeKey]?.let { name -> ThemePref.entries.firstOrNull { it.name == name } } ?: ThemePref.SYSTEM
    }

    override val timeoutSeconds: Flow<Int> = prefs.map { p ->
        (p[timeoutKey] ?: TIMEOUT_DEFAULT_S).coerceIn(TIMEOUT_MIN_S, TIMEOUT_MAX_S)
    }

    override suspend fun setTheme(theme: ThemePref) {
        dataStore.edit { it[themeKey] = theme.name }
    }

    override suspend fun setTimeout(seconds: Int) {
        dataStore.edit { it[timeoutKey] = seconds.coerceIn(TIMEOUT_MIN_S, TIMEOUT_MAX_S) }
    }
}
