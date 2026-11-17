package pt.ismai.farol.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.ismai.farol.data.firestore.SettingsMapper
import pt.ismai.farol.data.repository.SyncedSettings
import pt.ismai.farol.domain.model.ThemePref

class SettingsMapperTest {
    @Test fun roundTrip() {
        val settings = SyncedSettings(ThemePref.DARK, 20)
        assertEquals(settings, SettingsMapper.fromMap(SettingsMapper.toMap(settings)))
    }

    @Test fun partialSaveWritesOnlyTheFieldThatChanged() {
        assertEquals(mapOf("theme" to "LIGHT"), SettingsMapper.toMap(SyncedSettings(ThemePref.LIGHT, null)))
        assertEquals(mapOf("timeoutSeconds" to 30), SettingsMapper.toMap(SyncedSettings(null, 30)))
    }

    @Test fun serverDataIsValidated() {
        val bad = SettingsMapper.fromMap(mapOf("theme" to "PINK", "timeoutSeconds" to 100_000L))
        assertNull(bad.theme)
        assertEquals(120, bad.timeoutSeconds)
        val wrongTypes = SettingsMapper.fromMap(mapOf("theme" to 5, "timeoutSeconds" to "ten"))
        assertEquals(SyncedSettings(null, null), wrongTypes)
    }

    @Test fun emptyDocument_changesNothing() {
        assertEquals(SyncedSettings(null, null), SettingsMapper.fromMap(emptyMap()))
    }
}
