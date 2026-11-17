package pt.ismai.farol.data.firestore

import pt.ismai.farol.data.repository.SyncedSettings
import pt.ismai.farol.data.repository.TIMEOUT_MAX_S
import pt.ismai.farol.data.repository.TIMEOUT_MIN_S
import pt.ismai.farol.domain.model.ThemePref

/** Settings <-> Firestore map. Data from the server is never trusted: unknown values are dropped. */
object SettingsMapper {
    const val FIELD_THEME = "theme"
    const val FIELD_TIMEOUT = "timeoutSeconds"

    /** Only the fields that are set, so a partial save never erases the others. */
    fun toMap(settings: SyncedSettings): Map<String, Any> = buildMap {
        settings.theme?.let { put(FIELD_THEME, it.name) }
        settings.timeoutSeconds?.let { put(FIELD_TIMEOUT, it.coerceIn(TIMEOUT_MIN_S, TIMEOUT_MAX_S)) }
    }

    fun fromMap(map: Map<String, Any?>): SyncedSettings = SyncedSettings(
        theme = (map[FIELD_THEME] as? String)?.let { name -> ThemePref.entries.firstOrNull { it.name == name } },
        timeoutSeconds = (map[FIELD_TIMEOUT] as? Number)?.toInt()?.coerceIn(TIMEOUT_MIN_S, TIMEOUT_MAX_S),
    )
}
